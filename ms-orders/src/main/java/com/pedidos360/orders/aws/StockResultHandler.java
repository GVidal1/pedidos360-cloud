package com.pedidos360.orders.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.pedidos360.orders.OrdersApplication;
import com.pedidos360.orders.consumer.StockResultProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Lambda CONSUMIDORA de la cola SQS "pedidos360-orders-stock-result-queue" (disparador SQS, no API Gateway).
 *
 * Configuración en Lambda:
 *   - Handler: com.pedidos360.orders.aws.StockResultHandler::handleRequest
 *   - Runtime: Java 21, mismo jar "-aws.jar" que la Lambda de la API
 *   - Variables: las mismas del README (no publica nada: no necesita RABBITMQ_* ni ARN de tópicos)
 *   - Disparador: la cola SQS, con "Report batch item failures" activado
 *
 * Arranca el contexto Spring SIN servidor web (no hay HTTP aquí): por eso SecurityConfig y
 * AwsCorsFilter llevan @ConditionalOnWebApplication.
 */
public class StockResultHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger log = LoggerFactory.getLogger(StockResultHandler.class);

    private static final StockResultProcessor processor;

    static {
        // ServerlessAutoConfiguration (viene con aws-serverless-java-container) asume un contexto web y
        // revienta con ClassCastException si no lo hay: en la consumidora se excluye explícitamente.
        ConfigurableApplicationContext context = new SpringApplicationBuilder(OrdersApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.autoconfigure.exclude="
                        + "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
                .run();
        processor = context.getBean(StockResultProcessor.class);
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        log.info("Mensajes recibidos de SQS: {}", event.getRecords().size());
        List<SQSBatchResponse.BatchItemFailure> fallos = new ArrayList<>();
        for (SQSEvent.SQSMessage mensaje : event.getRecords()) {
            try {
                log.info("Procesando mensaje {}: {}", mensaje.getMessageId(), mensaje.getBody());
                processor.procesar(mensaje.getBody());
            } catch (Exception ex) {
                // Solo falla este mensaje: SQS reentrega únicamente los que se reportan aquí.
                log.error("Falló el mensaje {}: {}", mensaje.getMessageId(), ex.getMessage(), ex);
                fallos.add(new SQSBatchResponse.BatchItemFailure(mensaje.getMessageId()));
            }
        }
        return new SQSBatchResponse(fallos);
    }
}
