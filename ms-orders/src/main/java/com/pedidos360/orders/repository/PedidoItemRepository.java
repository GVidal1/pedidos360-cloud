package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    /**
     * Suma la cantidad de un producto que está "reservada": pedidos todavía no aceptados (CREADO) más
     * pedidos aceptados cuyo descuento de stock aún no confirma ms-catalog (stockConfirmado = false).
     * Sin la segunda parte habría una ventana, entre aceptar y recibir el evento de confirmación,
     * en la que ese stock volvería a verse disponible.
     */
    @Query("SELECT COALESCE(SUM(pi.cantidad), 0) FROM PedidoItem pi " +
            "WHERE pi.productoId = :productoId " +
            "AND (pi.pedido.estado = :creado OR pi.pedido.stockConfirmado = false)")
    int sumarCantidadReservada(@Param("productoId") Long productoId, @Param("creado") EstadoPedido creado);
}
