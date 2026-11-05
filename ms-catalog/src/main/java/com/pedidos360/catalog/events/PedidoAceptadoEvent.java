package com.pedidos360.catalog.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Evento que publica ms-orders en RabbitMQ (routing key pedido.aceptado; rabbit-bridge lo reenvía al tópico SNS "pedidos360-pedido-aceptado") cuando un pedido
 * pasa a ACEPTADO. Este es el contrato entre servicios: cada microservicio tiene su propia copia
 * del record (no comparten librería), así que cualquier cambio debe ser compatible hacia atrás.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PedidoAceptadoEvent(
        String eventId,
        String tipo,
        int version,
        String ocurridoEn,
        Long pedidoId,
        List<Item> items
) {
    public static final String TIPO = "PedidoAceptado";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productoId, Integer cantidad) {
    }
}
