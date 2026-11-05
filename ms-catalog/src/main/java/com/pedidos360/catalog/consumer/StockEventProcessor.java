package com.pedidos360.catalog.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.events.EventPublisher;
import com.pedidos360.catalog.events.PedidoAceptadoEvent;
import com.pedidos360.catalog.events.StockResultadoEvent;
import com.pedidos360.catalog.exception.ApiException;
import com.pedidos360.catalog.model.EventoProcesado;
import com.pedidos360.catalog.repository.EventoProcesadoRepository;
import com.pedidos360.catalog.service.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Lógica de la Lambda consumidora: recibe un PedidoAceptado (entregado por SQS), descuenta el stock una
 * sola vez (idempotente) y publica el resultado (StockDescontado / StockRechazado) en RabbitMQ; de ahí lo
 * toma rabbit-bridge hacia SNS/SQS para que ms-orders confirme o compense.
 *
 * Qué se reintenta y qué no:
 *  - Regla de negocio (sin stock, producto inexistente): NO se reintenta; se registra RECHAZADO y se informa.
 *  - Falla técnica (RDS caída, RabbitMQ no confirma): se lanza la excepción, SQS reentrega y, tras
 *    maxReceiveCount intentos, el mensaje cae a la DLQ.
 */
@Service
public class StockEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(StockEventProcessor.class);
    private static final int MAX_DETALLE = 500;

    private final CatalogService catalogService;
    private final EventoProcesadoRepository eventoRepository;
    private final EventPublisher publisher;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public StockEventProcessor(CatalogService catalogService,
                               EventoProcesadoRepository eventoRepository,
                               EventPublisher publisher,
                               PlatformTransactionManager transactionManager,
                               ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.eventoRepository = eventoRepository;
        this.publisher = publisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    /** @param body cuerpo del mensaje (SQS con "raw message delivery", o RabbitMQ en local): el JSON del evento tal cual. */
    public void procesar(String body) throws JsonProcessingException {
        PedidoAceptadoEvent evento = objectMapper.readValue(body, PedidoAceptadoEvent.class);
        if (evento.eventId() == null || evento.pedidoId() == null || evento.items() == null || evento.items().isEmpty()) {
            throw new IllegalArgumentException("Evento PedidoAceptado inválido: faltan eventId, pedidoId o items");
        }

        log.info("PedidoAceptado recibido: evento {} (pedido {}, {} item(s))", evento.eventId(), evento.pedidoId(), evento.items().size());

        // Si ya se procesó (reentrega de SQS), no se descuenta de nuevo; solo se reenvía el resultado
        // guardado, por si la vez anterior falló justo al publicar.
        EventoProcesado registro = eventoRepository.findById(evento.eventId())
                .orElseGet(() -> aplicar(evento));
        log.info("Pedido {}: resultado {}", registro.getPedidoId(), registro.getResultado());
        publicarResultado(registro);
    }

    private EventoProcesado aplicar(PedidoAceptadoEvent evento) {
        try {
            // Descuento + marca de "procesado" en la MISMA transacción: o quedan las dos cosas o ninguna.
            return tx.execute(status -> {
                catalogService.descontarStock(evento.items());
                return eventoRepository.save(registro(evento, EventoProcesado.DESCONTADO, null));
            });
        } catch (ApiException ex) {
            log.warn("Pedido {} rechazado por catálogo: {}", evento.pedidoId(), ex.getMessage());
            return tx.execute(status ->
                    eventoRepository.save(registro(evento, EventoProcesado.RECHAZADO, ex.getMessage())));
        }
    }

    private EventoProcesado registro(PedidoAceptadoEvent evento, String resultado, String detalle) {
        String recortado = detalle == null || detalle.length() <= MAX_DETALLE ? detalle : detalle.substring(0, MAX_DETALLE);
        return EventoProcesado.builder()
                .eventId(evento.eventId())
                .pedidoId(evento.pedidoId())
                .resultado(resultado)
                .detalle(recortado)
                .procesadoEn(Instant.now())
                .build();
    }

    private void publicarResultado(EventoProcesado registro) throws JsonProcessingException {
        String tipo = EventoProcesado.DESCONTADO.equals(registro.getResultado())
                ? StockResultadoEvent.STOCK_DESCONTADO
                : StockResultadoEvent.STOCK_RECHAZADO;
        StockResultadoEvent resultado = new StockResultadoEvent(
                "res-" + registro.getEventId(),   // determinista: una reentrega produce el mismo eventId
                tipo, 1, Instant.now().toString(),
                registro.getEventId(), registro.getPedidoId(), registro.getDetalle());
        publisher.publicar(tipo, objectMapper.writeValueAsString(resultado));
    }
}
