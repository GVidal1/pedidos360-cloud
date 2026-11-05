package com.pedidos360.orders.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @DynamicUpdate: cada UPDATE solo escribe las columnas que cambiaron. Así, si el operador cambia el
 * estado a la vez que el consumidor del resultado de stock marca stockConfirmado, no se pisan.
 */
@Entity
@DynamicUpdate
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String clienteUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private Instant actualizadoEn;

    /**
     * Confirmación del descuento de stock (ahora es asíncrono, vía eventos):
     *   false = el pedido se aceptó pero ms-catalog aún no confirma -> su cantidad sigue "reservada".
     *   true  = ms-catalog descontó el stock.
     *   null  = no aplica (pedido aún no aceptado o cancelado).
     */
    @Column(name = "stock_confirmado")
    private Boolean stockConfirmado;

    /** Se completa cuando el sistema cancela el pedido (p. ej. ms-catalog rechazó el descuento por falta de stock). */
    @Column(length = 500)
    private String motivoCancelacion;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PedidoItem> items = new ArrayList<>();

    public void agregarItem(PedidoItem item) {
        item.setPedido(this);
        this.items.add(item);
    }
}
