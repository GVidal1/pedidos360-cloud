package com.pedidos360.orders.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.orders.events.StockResultadoEvent;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Cierra el ciclo del evento (saga): ms-catalog informa si pudo descontar el stock.
 *  - StockDescontado: el pedido queda con el stock confirmado (deja de contar como "reservado").
 *  - StockRechazado : compensación, el pedido pasa a CANCELADO con el motivo.
 * Es idempotente: procesar dos veces el mismo resultado no cambia nada la segunda vez.
 */
@Service
public class StockResultProcessor {

    private static final Logger log = LoggerFactory.getLogger(StockResultProcessor.class);
    private static final int MAX_MOTIVO = 500;

    private final PedidoRepository pedidoRepository;
    private final ObjectMapper objectMapper;

    public StockResultProcessor(PedidoRepository pedidoRepository, ObjectMapper objectMapper) {
        this.pedidoRepository = pedidoRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void procesar(String body) throws JsonProcessingException {
        StockResultadoEvent evento = objectMapper.readValue(body, StockResultadoEvent.class);
        if (evento.pedidoId() == null || evento.tipo() == null) {
            throw new IllegalArgumentException("Evento de resultado inválido: faltan pedidoId o tipo");
        }

        log.info("Resultado de stock recibido: {} (pedido {}, evento {})", evento.tipo(), evento.pedidoId(), evento.eventId());

        Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
        if (pedido == null) {
            log.warn("Resultado de stock para un pedido inexistente ({}); se ignora", evento.pedidoId());
            return;
        }

        switch (evento.tipo()) {
            case StockResultadoEvent.STOCK_DESCONTADO -> confirmar(pedido);
            case StockResultadoEvent.STOCK_RECHAZADO -> compensar(pedido, evento.motivo());
            default -> log.warn("Tipo de evento desconocido: {}", evento.tipo());
        }
    }

    private void confirmar(Pedido pedido) {
        if (Boolean.FALSE.equals(pedido.getStockConfirmado())) {
            pedido.setStockConfirmado(true);
            pedidoRepository.save(pedido);
            log.info("Pedido {}: stock confirmado por el catálogo", pedido.getId());
        } else {
            log.info("Pedido {}: confirmación repetida o no aplicable, no se hace nada", pedido.getId());
        }
    }

    private void compensar(Pedido pedido, String motivo) {
        if (pedido.getEstado() == EstadoPedido.CANCELADO || Boolean.TRUE.equals(pedido.getStockConfirmado())) {
            return; // ya cancelado o ya confirmado: reentrega o evento contradictorio
        }
        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            log.error("El pedido {} ya fue ENTREGADO y el stock fue rechazado: requiere revisión manual", pedido.getId());
            return;
        }
        String texto = "Sin stock al aceptar" + (motivo == null ? "" : ": " + motivo);
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido.setStockConfirmado(null);
        pedido.setMotivoCancelacion(texto.length() <= MAX_MOTIVO ? texto : texto.substring(0, MAX_MOTIVO));
        pedido.setActualizadoEn(Instant.now());
        pedidoRepository.save(pedido);
        log.warn("Pedido {} cancelado por falta de stock", pedido.getId());
    }
}
