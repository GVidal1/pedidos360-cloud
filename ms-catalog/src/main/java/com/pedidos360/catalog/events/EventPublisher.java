package com.pedidos360.catalog.events;

/**
 * Salida de eventos hacia RabbitMQ. Con la propiedad pedidos360.rabbit.enabled=true publica de verdad
 * (RabbitEventPublisher); si no, solo deja un log (LogEventPublisher).
 *
 * El servicio ya no conoce SNS: RabbitMQ es su único bus. El reenvío RabbitMQ -> SNS -> SQS lo hace
 * el servicio rabbit-bridge (en la EC2 del broker), así que SNS/SQS siguen siendo la entrega hacia las Lambdas.
 */
public interface EventPublisher {

    /**
     * @param tipo        tipo de evento (PedidoAceptado, StockDescontado, StockRechazado); define la routing key
     *                    y viaja también en el cuerpo JSON, que es lo que usa el filtro de las suscripciones SNS
     * @param mensajeJson cuerpo del evento serializado como JSON
     */
    void publicar(String tipo, String mensajeJson);
}
