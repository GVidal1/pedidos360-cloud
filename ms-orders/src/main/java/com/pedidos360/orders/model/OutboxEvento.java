package com.pedidos360.orders.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Patrón "outbox": el evento se guarda en la MISMA transacción que el cambio de estado del pedido,
 * y recién después del commit se publica en RabbitMQ. Así nunca queda un pedido ACEPTADO sin evento
 * (si RabbitMQ falla, la fila queda pendiente y se reintenta) ni un evento de un pedido que no se guardó.
 */
@Entity
@Table(name = "outbox_eventos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvento {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private Instant creadoEn;

    /** null = pendiente de publicar. */
    private Instant publicadoEn;
}
