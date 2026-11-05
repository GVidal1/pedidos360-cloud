package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.OutboxEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, String> {

    List<OutboxEvento> findByPublicadoEnIsNullOrderByCreadoEnAsc();
}
