package com.pedidos360.orders.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Vista de un producto tal como lo responde ms-catalog (GET /api/catalog/products/{id}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoRemoto(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Boolean activo
) {
}
