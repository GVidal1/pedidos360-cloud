package com.pedidos360.orders.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.orders.model.OutboxEvento;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.repository.OutboxEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Outbox de ms-orders:
 *  1) encolarPedidoAceptado(): guarda el evento en la transacción del cambio de estado.
 *  2) publicarPendientes(): tras el COMMIT publica en RabbitMQ (con confirmación del broker) y marca como publicado.
 * Desde RabbitMQ, rabbit-bridge lo reenvía a SNS -> SQS -> ms-catalog.
 *
 * El eventId es DETERMINISTA ("pedido-<id>"): un pedido solo se acepta una vez, así que si el evento
 * se vuelve a enviar (reconciliación) ms-catalog lo reconoce y NO descuenta el stock dos veces.
 *
 * En Lambda la publicación se hace de forma SÍNCRONA antes de responder (un hilo en segundo plano
 * se congelaría cuando Lambda termina la invocación). Si RabbitMQ falla, la fila queda pendiente y se
 * reintenta en el siguiente cambio a ACEPTADO o con POST /api/orders/outbox/reintentar.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventoRepository outboxRepository;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate nuevaTransaccion;

    public OutboxService(OutboxEventoRepository outboxRepository,
                         EventPublisher publisher,
                         ObjectMapper objectMapper,
                         PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        // Dentro de afterCommit la transacción original ya terminó: lo que se escriba debe ir en una nueva.
        this.nuevaTransaccion = new TransactionTemplate(transactionManager);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Debe llamarse dentro de la transacción que cambia el estado del pedido. */
    public void registrarPedidoAceptado(Pedido pedido) {
        encolarPedidoAceptado(pedido);
        publicarTrasCommit();
    }

    /**
     * Guarda (o vuelve a dejar pendiente) el evento PedidoAceptado del pedido. Si ya existía una fila
     * con ese eventId se reutiliza: se refresca el contenido y queda pendiente de publicar otra vez.
     */
    public void encolarPedidoAceptado(Pedido pedido) {
        String id = "pedido-" + pedido.getId();
        Instant ahora = Instant.now();
        PedidoAceptadoEvent evento = new PedidoAceptadoEvent(
                id, PedidoAceptadoEvent.TIPO, 1, ahora.toString(), pedido.getId(),
                pedido.getItems().stream()
                        .map(i -> new PedidoAceptadoEvent.Item(i.getProductoId(), i.getCantidad()))
                        .toList());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + PedidoAceptadoEvent.TIPO, e);
        }

        OutboxEvento fila = outboxRepository.findById(id).orElseGet(() -> OutboxEvento.builder()
                .id(id)
                .tipo(PedidoAceptadoEvent.TIPO)
                .creadoEn(ahora)
                .build());
        fila.setPayload(payload);
        fila.setPublicadoEn(null);
        outboxRepository.save(fila);
        log.info("Evento {} guardado en el outbox (pedido {}), pendiente de publicar", id, pedido.getId());
    }

    /** Solo si la transacción confirma se publica. Si hace rollback, el evento tampoco existe. */
    public void publicarTrasCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publicarPendientes();
            }
        });
    }

    /** Publica todo lo pendiente. Un fallo de RabbitMQ no debe romper la respuesta al usuario. */
    public int publicarPendientes() {
        List<OutboxEvento> pendientes = nuevaTransaccion.execute(
                status -> outboxRepository.findByPublicadoEnIsNullOrderByCreadoEnAsc());
        if (pendientes == null || pendientes.isEmpty()) {
            log.info("Outbox: no hay eventos pendientes de publicar");
            return 0;
        }
        log.info("Outbox: {} evento(s) pendiente(s) de publicar en RabbitMQ", pendientes.size());

        int publicados = 0;
        for (OutboxEvento evento : pendientes) {
            try {
                publisher.publicar(evento.getTipo(), evento.getPayload());
                nuevaTransaccion.executeWithoutResult(status ->
                        outboxRepository.findById(evento.getId()).ifPresent(e -> e.setPublicadoEn(Instant.now())));
                publicados++;
            } catch (Exception ex) {
                // Se corta en el primer fallo para no acumular timeouts; queda pendiente para el próximo intento.
                log.error("No se pudo publicar el evento {} ({}): {}", evento.getId(), evento.getTipo(), ex.toString());
                break;
            }
        }
        log.info("Outbox: {} publicado(s), {} pendiente(s)", publicados, pendientes.size() - publicados);
        return publicados;
    }
}
