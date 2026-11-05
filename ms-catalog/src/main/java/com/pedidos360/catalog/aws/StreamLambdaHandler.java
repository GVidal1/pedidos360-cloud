package com.pedidos360.catalog.aws;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pedidos360.catalog.CatalogApplication;

/**
 * Punto de entrada de este microservicio cuando corre detrás de API Gateway + Lambda.
 *
 * Configuración en Lambda:
 *   - Handler: com.pedidos360.catalog.aws.StreamLambdaHandler::handleRequest
 *   - Runtime: Java 21
 *   - Artefacto: el jar con clasificador "aws" (ms-pedidos360-catalog-1.0.0-aws.jar)
 *   - Variables de entorno: ver README.md
 */
public class StreamLambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(CatalogApplication.class);
        } catch (ContainerInitializationException e) {
            throw new RuntimeException("Error inicializando el contenedor Spring Boot en Lambda", e);
        }
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest awsProxyRequest, Context context) {
        return handler.proxy(awsProxyRequest, context);
    }
}
