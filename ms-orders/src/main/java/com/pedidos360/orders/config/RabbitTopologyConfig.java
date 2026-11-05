package com.pedidos360.orders.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ. RabbitAdmin (viene con spring-boot-starter-amqp) la declara sola la primera
 * vez que el servicio abre conexión; declarar es idempotente siempre que los argumentos sean iguales,
 * por eso AMBOS microservicios llevan esta misma clase.
 *
 *   exchange pedidos360.eventos (topic)
 *     pedido.aceptado  -> cola pedidos360.pedido-aceptado   (la vacía rabbit-bridge hacia SNS)
 *     stock.*          -> cola pedidos360.stock-resultado   (la vacía rabbit-bridge hacia SNS)
 *   exchange pedidos360.eventos.dlx (direct)
 *     <cola>.dlq       <- mensajes rechazados o vencidos (TTL) de cada cola
 */
@Configuration
@ConditionalOnProperty(name = "pedidos360.rabbit.enabled", havingValue = "true")
public class RabbitTopologyConfig {

    public static final String RK_PEDIDO_ACEPTADO = "pedido.aceptado";
    public static final String RK_STOCK_DESCONTADO = "stock.descontado";
    public static final String RK_STOCK_RECHAZADO = "stock.rechazado";

    /** tipo de evento (el que viaja en el JSON) -> routing key. */
    public static String routingKey(String tipo) {
        return switch (tipo) {
            case "PedidoAceptado" -> RK_PEDIDO_ACEPTADO;
            case "StockDescontado" -> RK_STOCK_DESCONTADO;
            case "StockRechazado" -> RK_STOCK_RECHAZADO;
            default -> throw new IllegalArgumentException("Tipo de evento sin routing key: " + tipo);
        };
    }

    @Bean
    TopicExchange eventosExchange(@Value("${pedidos360.rabbit.exchange}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(@Value("${pedidos360.rabbit.dead-letter-exchange}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    // ---- PedidoAceptado (ms-orders -> ms-catalog) ----

    @Bean
    Queue pedidoAceptadoQueue(@Value("${pedidos360.rabbit.pedido-aceptado-queue}") String nombre,
                              @Value("${pedidos360.rabbit.dead-letter-exchange}") String dlx,
                              @Value("${pedidos360.rabbit.message-ttl-ms}") int ttl) {
        return conDeadLetter(nombre, dlx, ttl);
    }

    @Bean
    Queue pedidoAceptadoDlq(@Value("${pedidos360.rabbit.pedido-aceptado-queue}") String nombre) {
        return QueueBuilder.durable(nombre + ".dlq").build();
    }

    @Bean
    Binding pedidoAceptadoBinding(Queue pedidoAceptadoQueue, TopicExchange eventosExchange) {
        return BindingBuilder.bind(pedidoAceptadoQueue).to(eventosExchange).with(RK_PEDIDO_ACEPTADO);
    }

    @Bean
    Binding pedidoAceptadoDlqBinding(Queue pedidoAceptadoDlq, Queue pedidoAceptadoQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(pedidoAceptadoDlq).to(deadLetterExchange).with(pedidoAceptadoQueue.getName());
    }

    // ---- StockDescontado / StockRechazado (ms-catalog -> ms-orders) ----

    @Bean
    Queue stockResultadoQueue(@Value("${pedidos360.rabbit.stock-resultado-queue}") String nombre,
                              @Value("${pedidos360.rabbit.dead-letter-exchange}") String dlx,
                              @Value("${pedidos360.rabbit.message-ttl-ms}") int ttl) {
        return conDeadLetter(nombre, dlx, ttl);
    }

    @Bean
    Queue stockResultadoDlq(@Value("${pedidos360.rabbit.stock-resultado-queue}") String nombre) {
        return QueueBuilder.durable(nombre + ".dlq").build();
    }

    @Bean
    Binding stockResultadoBinding(Queue stockResultadoQueue, TopicExchange eventosExchange) {
        return BindingBuilder.bind(stockResultadoQueue).to(eventosExchange).with("stock.*");
    }

    @Bean
    Binding stockResultadoDlqBinding(Queue stockResultadoDlq, Queue stockResultadoQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(stockResultadoDlq).to(deadLetterExchange).with(stockResultadoQueue.getName());
    }

    /** Cola durable; lo rechazado o vencido (TTL) se desvía a la DLQ en vez de perderse o reintentarse sin fin. */
    private static Queue conDeadLetter(String nombre, String dlx, int ttl) {
        return QueueBuilder.durable(nombre)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(nombre)
                .ttl(ttl)
                .build();
    }
}
