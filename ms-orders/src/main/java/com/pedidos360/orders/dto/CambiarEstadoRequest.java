package com.pedidos360.orders.dto;

import jakarta.validation.constraints.NotNull;
import com.pedidos360.orders.model.EstadoPedido;

public record CambiarEstadoRequest(
        @NotNull(message = "El nuevo estado es obligatorio")
        EstadoPedido estado
) {
}
