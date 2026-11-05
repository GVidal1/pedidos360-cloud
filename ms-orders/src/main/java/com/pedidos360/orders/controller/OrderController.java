package com.pedidos360.orders.controller;

import com.pedidos360.orders.dto.CambiarEstadoRequest;
import com.pedidos360.orders.dto.CrearPedidoRequest;
import com.pedidos360.orders.dto.PedidoResponse;
import com.pedidos360.orders.events.OutboxService;
import com.pedidos360.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OutboxService outboxService;

    public OrderController(OrderService orderService, OutboxService outboxService) {
        this.orderService = orderService;
        this.outboxService = outboxService;
    }

    /** Cliente ve solo sus pedidos; Admin/Operador ven todos. */
    @GetMapping
    public ResponseEntity<List<PedidoResponse>> listar(Authentication auth) {
        boolean verTodos = tieneRol(auth, "ADMIN", "OPERADOR");
        return ResponseEntity.ok(orderService.listarPara(auth.getName(), verTodos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtener(@PathVariable Long id, Authentication auth) {
        boolean verTodos = tieneRol(auth, "ADMIN", "OPERADOR");
        return ResponseEntity.ok(orderService.obtenerPara(id, auth.getName(), verTodos));
    }

    /** Crear pedido: Cliente, Operador y Admin (según la guía, todos pueden crear). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','CLIENTE')")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request, Authentication auth) {
        return ResponseEntity.ok(orderService.crear(auth.getName(), request));
    }

    /** Cambiar estado: solo Admin y Operador gestionan el flujo operativo. */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<PedidoResponse> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return ResponseEntity.ok(orderService.cambiarEstado(id, request.estado()));
    }

    /** Reintenta publicar en RabbitMQ los eventos que quedaron pendientes (p. ej. si el broker falló al aceptar). Solo Admin. */
    @PostMapping("/outbox/reintentar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> reintentarOutbox() {
        return ResponseEntity.ok(Map.of("publicados", outboxService.publicarPendientes()));
    }

    /** Reconciliación: vuelve a emitir el evento de los pedidos aceptados sin stock confirmado. Solo Admin. */
    @PostMapping("/stock/reconciliar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> reconciliarStock() {
        return ResponseEntity.ok(Map.of("reencolados", orderService.reencolarStockPendiente()));
    }

    private boolean tieneRol(Authentication auth, String... roles) {
        for (String rol : roles) {
            String target = "ROLE_" + rol;
            boolean match = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(target::equals);
            if (match) return true;
        }
        return false;
    }
}
