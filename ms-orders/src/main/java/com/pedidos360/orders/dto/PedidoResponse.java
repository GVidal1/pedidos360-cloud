package com.pedidos360.orders.dto;

import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.model.PedidoItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(
        Long id,
        String clienteUsername,
        EstadoPedido estado,
        BigDecimal total,
        Instant creadoEn,
        Instant actualizadoEn,
        List<ItemResponse> items,
        Boolean stockConfirmado,
        String motivoCancelacion
) {
    public record ItemResponse(Long productoId, String productoNombre, Integer cantidad, BigDecimal precioUnitario) {
        static ItemResponse from(PedidoItem item) {
            return new ItemResponse(item.getProductoId(), item.getProductoNombre(), item.getCantidad(), item.getPrecioUnitario());
        }
    }

    public static PedidoResponse from(Pedido pedido) {
        List<ItemResponse> items = pedido.getItems().stream().map(ItemResponse::from).toList();
        return new PedidoResponse(
                pedido.getId(),
                pedido.getClienteUsername(),
                pedido.getEstado(),
                pedido.getTotal(),
                pedido.getCreadoEn(),
                pedido.getActualizadoEn(),
                items,
                pedido.getStockConfirmado(),
                pedido.getMotivoCancelacion()
        );
    }
}
