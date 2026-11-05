package com.pedidos360.catalog.events;

import com.pedidos360.catalog.config.RabbitTopologyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publica en el exchange de RabbitMQ y ESPERA la confirmación del broker (publisher confirm) antes de
 * volver. Eso importa por dos motivos:
 *  - el outbox (ms-orders) solo marca el evento como publicado si el broker lo aceptó de verdad;
 *  - en Lambda no se puede publicar "en segundo plano": al terminar la invocación el entorno se congela.
 *
 * Host, usuario y TLS vienen de las variables RABBITMQ_* (ver application.yml). La conexión se abre en
 * el primer uso y se reutiliza mientras el contenedor de Lambda siga caliente.
 */
@Component
@ConditionalOnProperty(name = "pedidos360.rabbit.enabled", havingValue = "true")
public class RabbitEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);
    private static final long CONFIRM_TIMEOUT_MS = 5000;

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${pedidos360.rabbit.exchange}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Override
    public void publicar(String tipo, String mensajeJson) {
        String routingKey = RabbitTopologyConfig.routingKey(tipo);
        Message mensaje = MessageBuilder
                .withBody(mensajeJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("tipo", tipo)
                .build();

        // invoke(): envío y espera de la confirmación en el MISMO canal.
        boolean confirmado = Boolean.TRUE.equals(rabbitTemplate.invoke(ops -> {
            ops.send(exchange, routingKey, mensaje);
            return ops.waitForConfirms(CONFIRM_TIMEOUT_MS);
        }));
        if (!confirmado) {
            throw new IllegalStateException("RabbitMQ no confirmó el evento " + tipo + " (exchange " + exchange + ")");
        }
        log.info("Evento {} publicado en RabbitMQ (exchange={}, routingKey={})", tipo, exchange, routingKey);
    }
}
