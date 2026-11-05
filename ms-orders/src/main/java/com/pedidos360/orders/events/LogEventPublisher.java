package com.pedidos360.orders.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RabbitMQ desactivado (pedidos360.rabbit.enabled=false, el valor por defecto en local): el evento solo se registra en el log. */
@Component
@ConditionalOnProperty(name = "pedidos360.rabbit.enabled", havingValue = "false", matchIfMissing = true)
public class LogEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LogEventPublisher.class);

    @Override
    public void publicar(String tipo, String mensajeJson) {
        log.info("[sin RabbitMQ] Evento {} (no se envía a ningún broker): {}", tipo, mensajeJson);
    }
}
