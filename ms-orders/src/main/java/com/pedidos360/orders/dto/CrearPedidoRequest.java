package com.pedidos360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CrearPedidoRequest(
        @NotEmpty(message = "El pedido debe tener al menos un ítem")
        @Valid
        List<PedidoItemRequest> items
) {
}
