package com.pedidos360.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Puente RabbitMQ -> SNS. Es el tramo que mantiene SNS y SQS dentro del flujo:
 *
 *   cola pedidos360.pedido-aceptado  ->  tópico SNS pedidos360-pedido-aceptado  (-> SQS de ms-catalog)
 *   cola pedidos360.stock-resultado  ->  tópico SNS pedidos360-stock-resultado  (-> SQS de ms-orders)
 *
 * Corre como proceso normal en la misma EC2 que RabbitMQ (java -jar). Las credenciales de AWS salen del
 * rol de la instancia (LabRole / LabInstanceProfile), no hay claves en el código.
 *
 * Garantías: el mensaje se confirma (ack) a RabbitMQ SOLO después de que SNS lo aceptó (al menos una vez;
 * un duplicado es inofensivo porque los consumidores son idempotentes por eventId).
 *  - Falla transitoria (SNS no responde): el mensaje se reencola y se reintenta tras una pausa.
 *  - Mensaje inválido (JSON roto, sin "tipo"): se rechaza sin reencolar y RabbitMQ lo manda a su DLQ.
 *
 * Variables de entorno: ver README (RABBITMQ_*, PEDIDO_ACEPTADO_TOPIC_ARN, STOCK_RESULTADO_TOPIC_ARN).
 */
public class BridgeApplication {

    private static final Logger log = LoggerFactory.getLogger(BridgeApplication.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long PAUSA_REINTENTO_MS = 5000;

    public static void main(String[] args) throws Exception {
        String exchange = env("RABBITMQ_EXCHANGE", "pedidos360.eventos");
        String dlx = env("RABBITMQ_DLX", "pedidos360.eventos.dlx");
        String colaPedidos = env("RABBITMQ_PEDIDO_ACEPTADO_QUEUE", "pedidos360.pedido-aceptado");
        String colaStock = env("RABBITMQ_STOCK_RESULTADO_QUEUE", "pedidos360.stock-resultado");
        int ttl = Integer.parseInt(env("RABBITMQ_MESSAGE_TTL_MS", "3600000"));
        String topicPedidos = requerida("PEDIDO_ACEPTADO_TOPIC_ARN");
        String topicStock = requerida("STOCK_RESULTADO_TOPIC_ARN");

        SnsClient sns = SnsClient.builder()
                .region(Region.of(env("AWS_REGION", "us-east-1")))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();

        Connection conexion = conectar();
        Channel canal = conexion.createChannel();
        canal.basicQos(10);

        // La misma topología que declaran los microservicios (mismos argumentos: declarar es idempotente).
        canal.exchangeDeclare(exchange, "topic", true);
        canal.exchangeDeclare(dlx, "direct", true);
        declararCola(canal, colaPedidos, dlx, ttl);
        declararCola(canal, colaStock, dlx, ttl);
        canal.queueBind(colaPedidos, exchange, "pedido.aceptado");
        canal.queueBind(colaStock, exchange, "stock.*");

        consumir(canal, sns, colaPedidos, topicPedidos);
        consumir(canal, sns, colaStock, topicStock);
        log.info("Puente iniciado: {} -> {} | {} -> {}", colaPedidos, topicPedidos, colaStock, topicStock);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                conexion.close();
            } catch (Exception ignorada) {
                // cierre best-effort
            }
        }));
        new CountDownLatch(1).await(); // el consumo corre en los hilos del cliente AMQP
    }

    private static void declararCola(Channel canal, String cola, String dlx, int ttl) throws Exception {
        canal.queueDeclare(cola, true, false, false, Map.of(
                "x-dead-letter-exchange", dlx,
                "x-dead-letter-routing-key", cola,
                "x-message-ttl", ttl));
        canal.queueDeclare(cola + ".dlq", true, false, false, null);
        canal.queueBind(cola + ".dlq", dlx, cola);
    }

    private static void consumir(Channel canal, SnsClient sns, String cola, String topicArn) throws Exception {
        DeliverCallback alRecibir = (consumerTag, entrega) -> {
            long tag = entrega.getEnvelope().getDeliveryTag();
            String cuerpo = new String(entrega.getBody(), StandardCharsets.UTF_8);
            try {
                String tipo = tipoDe(cuerpo);
                var respuesta = sns.publish(PublishRequest.builder()
                        .topicArn(topicArn)
                        .message(cuerpo)
                        .messageAttributes(Map.of("tipo", MessageAttributeValue.builder()
                                .dataType("String").stringValue(tipo).build()))
                        .build());
                canal.basicAck(tag, false);
                log.info("Evento {} reenviado de {} a SNS (messageId={})", tipo, cola, respuesta.messageId());
            } catch (IllegalArgumentException invalido) {
                log.error("Mensaje inválido en {}, va a la DLQ: {}", cola, invalido.getMessage());
                canal.basicNack(tag, false, false);
            } catch (Exception transitoria) {
                log.error("No se pudo reenviar a SNS (se reintenta): {}", transitoria.toString());
                try {
                    Thread.sleep(PAUSA_REINTENTO_MS);
                } catch (InterruptedException interrumpido) {
                    Thread.currentThread().interrupt();
                }
                canal.basicNack(tag, false, true);
            }
        };
        canal.basicConsume(cola, false, alRecibir, consumerTag -> log.warn("Consumo de {} cancelado", cola));
    }

    /** Sin el campo "tipo" el filtro de la suscripción SNS descartaría el mensaje en silencio. */
    private static String tipoDe(String cuerpo) {
        try {
            String tipo = MAPPER.readTree(cuerpo).path("tipo").asText(null);
            if (tipo == null || tipo.isBlank()) {
                throw new IllegalArgumentException("el evento no trae el campo \"tipo\"");
            }
            return tipo;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("el cuerpo no es un JSON válido: " + e.getMessage(), e);
        }
    }

    /** Espera a que el broker esté disponible (p. ej. si el puente arranca antes que RabbitMQ). */
    private static Connection conectar() throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(env("RABBITMQ_HOST", "localhost"));
        f.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        f.setUsername(env("RABBITMQ_USERNAME", "guest"));
        f.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        f.setVirtualHost(env("RABBITMQ_VHOST", "/"));
        if (Boolean.parseBoolean(env("RABBITMQ_SSL", "false"))) {
            f.useSslProtocol();
        }
        f.setAutomaticRecoveryEnabled(true);   // se reconecta y vuelve a suscribirse si el broker cae
        f.setTopologyRecoveryEnabled(true);
        f.setRequestedHeartbeat(30);
        while (true) {
            try {
                return f.newConnection("pedidos360-rabbit-bridge");
            } catch (Exception e) {
                log.warn("RabbitMQ no disponible ({}), reintento en 5 s", e.toString());
                Thread.sleep(PAUSA_REINTENTO_MS);
            }
        }
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v == null || v.isBlank() ? porDefecto : v;
    }

    private static String requerida(String nombre) {
        String v = System.getenv(nombre);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + nombre);
        }
        return v;
    }
}
