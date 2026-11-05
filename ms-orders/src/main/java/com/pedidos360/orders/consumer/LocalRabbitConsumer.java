package com.pedidos360.orders.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * SOLO para desarrollo local (RABBITMQ_ENABLED=true y RABBITMQ_LOCAL_CONSUMER=true con un RabbitMQ en Docker):
 * escucha la cola pedidos360.stock-resultado y confirma o cancela el pedido (lo que en AWS hace la Lambda disparada por SQS).
 * Así se puede probar el flujo completo sin AWS. En AWS este componente NO se crea: ahí la cola la
 * vacía rabbit-bridge (hacia SNS) y el procesamiento lo hace la Lambda disparada por SQS.
 *
 * Si el procesamiento lanza una excepción, el mensaje NO se reencola (default-requeue-rejected=false):
 * RabbitMQ lo manda a la DLQ de la cola.
 */
@Component
@ConditionalOnProperty(name = "pedidos360.rabbit.local-consumer", havingValue = "true")
public class LocalRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(LocalRabbitConsumer.class);

    private final StockResultProcessor processor;

    public LocalRabbitConsumer(StockResultProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "${pedidos360.rabbit.stock-resultado-queue}")
    public void alRecibir(Message mensaje) throws Exception {
        String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        log.info("[local] Mensaje recibido de RabbitMQ: {}", cuerpo);
        processor.procesar(cuerpo);
    }
}
