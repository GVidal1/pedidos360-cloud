package com.pedidos360.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Resultado que ms-catalog publica en "pedidos360-stock-resultado" tras intentar descontar stock.
 * tipo = StockDescontado | StockRechazado ("motivo" solo viene si fue rechazado).
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
