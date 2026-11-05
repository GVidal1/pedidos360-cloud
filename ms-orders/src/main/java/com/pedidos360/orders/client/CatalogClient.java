package com.pedidos360.orders.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.orders.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Único punto por el que ms-orders habla con ms-catalog de forma SÍNCRONA (solo consultas: el
 * descuento de stock ahora viaja por eventos RabbitMQ + SNS/SQS). Reenvía el JWT del usuario que originó la petición, así
 * ms-catalog lo valida por su cuenta y aplica sus propios roles.
 *
 * URL base (pedidos360.catalog.base-url):
 *   - local: http://localhost:8081
 *   - AWS:   invoke URL del API Gateway (ver README: requiere salida a internet desde la VPC)
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CatalogClient(@Value("${pedidos360.catalog.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        // API Gateway corta a los 30 s; se deja margen para un arranque en frío de ms-catalog.
        factory.setReadTimeout(28_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public ProductoRemoto obtenerProducto(Long id) {
        String token = tokenActual();
        return ejecutar(() -> restClient.get()
                .uri("/api/catalog/products/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ProductoRemoto.class));
    }

    private String tokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        throw ApiException.unauthorized("No hay un token válido para consultar el catálogo");
    }

    private <T> T ejecutar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException ex) {
            throw traducir(ex);
        } catch (ResourceAccessException ex) {
            log.error("No se pudo conectar con ms-catalog: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No fue posible comunicarse con el servicio de catálogo");
        }
    }

    /** Conserva el código y el mensaje que devolvió ms-catalog (404, 409, 403...) para que el usuario los vea igual que antes. */
    private ApiException traducir(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            log.error("ms-catalog respondió {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiException(HttpStatus.BAD_GATEWAY, "El servicio de catálogo respondió con un error");
        }
        return new ApiException(status, extraerMensaje(ex.getResponseBodyAsString(), status));
    }

    private String extraerMensaje(String body, HttpStatus status) {
        try {
            JsonNode message = objectMapper.readTree(body).path("message");
            if (!message.isMissingNode() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // el cuerpo no era JSON: se usa el mensaje genérico
        }
        return "El servicio de catálogo rechazó la solicitud (" + status.value() + ")";
    }
}
