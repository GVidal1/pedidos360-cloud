package com.pedidos360.orders.model;

import java.util.List;
import java.util.Map;

/**
 * Máquina de estados del pedido (Caso 0 - Pedidos360):
 * CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO
 * Desde CREADO se puede pasar a CANCELADO. Una vez ACEPTADO (momento en que se
 * descuenta stock), el pedido ya no se puede cancelar: solo avanza hacia adelante
 * hasta ENTREGADO. Esto evita dejar el stock descontado de forma permanente sin
 * que el pedido se concrete.
 * Regla clave de la guía: "No se puede DESPACHAR sin ACEPTAR" -> en general,
 * no se pueden saltar estados hacia adelante.
 */
public enum EstadoPedido {
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    private static final Map<EstadoPedido, List<EstadoPedido>> TRANSICIONES_VALIDAS = Map.of(
            CREADO, List.of(ACEPTADO, CANCELADO),
            ACEPTADO, List.of(EN_PREPARACION),
            EN_PREPARACION, List.of(DESPACHADO),
            DESPACHADO, List.of(ENTREGADO),
            ENTREGADO, List.of(),
            CANCELADO, List.of()
    );

    public boolean puedeTransicionarA(EstadoPedido siguiente) {
        return TRANSICIONES_VALIDAS.getOrDefault(this, List.of()).contains(siguiente);
    }

    public boolean esFinal() {
        return this == ENTREGADO || this == CANCELADO;
    }
}
