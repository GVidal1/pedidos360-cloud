package com.pedidos360.catalog.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Evento que publica ms-catalog en RabbitMQ (routing key stock.descontado / stock.rechazado; rabbit-bridge lo reenvía al tópico SNS "pedidos360-stock-resultado") después de intentar
 * descontar el stock de un pedido. ms-orders lo consume para confirmar o compensar (cancelar).
 *
 * tipo = StockDescontado | StockRechazado. "motivo" solo viene cuando fue rechazado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockResultadoEvent(
        String eventId,
        String tipo,
        int version,
        String ocurridoEn,
        String eventoOrigenId,
        Long pedidoId,
        String motivo
) {
    public static final String STOCK_DESCONTADO = "StockDescontado";
    public static final String STOCK_RECHAZADO = "StockRechazado";
}
