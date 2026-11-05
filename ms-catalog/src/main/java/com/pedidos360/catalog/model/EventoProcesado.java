package com.pedidos360.catalog.model;

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
 * Registro de eventos ya procesados (idempotencia). SQS entrega "al menos una vez", así que el
 * mismo PedidoAceptado puede llegar dos veces: si ya existe aquí, NO se vuelve a descontar stock;
 * solo se reenvía el resultado guardado.
 */
@Entity
@Table(name = "eventos_procesados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoProcesado {

    public static final String DESCONTADO = "DESCONTADO";
    public static final String RECHAZADO = "RECHAZADO";

    /** eventId del evento PedidoAceptado recibido. */
    @Id
    @Column(length = 64)
    private String eventId;

    @Column(nullable = false)
    private Long pedidoId;

    /** DESCONTADO o RECHAZADO. */
    @Column(nullable = false, length = 20)
    private String resultado;

    @Column(length = 500)
    private String detalle;

    @Column(nullable = false)
    private Instant procesadoEn;
}
