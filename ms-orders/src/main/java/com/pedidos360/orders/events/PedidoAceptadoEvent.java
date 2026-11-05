package com.pedidos360.orders.events;

import java.util.List;

/**
 * Evento que ms-orders publica en RabbitMQ (routing key pedido.aceptado; rabbit-bridge lo reenvía al tópico SNS "pedidos360-pedido-aceptado") cuando un pedido
 * pasa a ACEPTADO. Es el contrato con ms-catalog (cada servicio tiene su propia copia del record):
 * solo se pueden AGREGAR campos, nunca quitar ni renombrar.
 */
public record PedidoAceptadoEvent(
        String eventId,
        String tipo,
        int version,
        String ocurridoEn,
        Long pedidoId,
        List<Item> items
) {
    public static final String TIPO = "PedidoAceptado";

    public record Item(Long productoId, Integer cantidad) {
    }
}
