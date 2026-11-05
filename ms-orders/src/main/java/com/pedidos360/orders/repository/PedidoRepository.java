package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByClienteUsernameOrderByCreadoEnDesc(String clienteUsername);

    List<Pedido> findAllByOrderByCreadoEnDesc();

    /** Pedidos aceptados cuyo descuento de stock todavía no fue confirmado por ms-catalog. */
    List<Pedido> findByStockConfirmadoFalse();
}
