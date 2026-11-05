package com.pedidos360.catalog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filtro propio que escribe los headers CORS de forma determinista (sin depender de
 * API Gateway ni de Spring Security). Es el primer filtro de la cadena y responde
 * directamente los preflight OPTIONS, con un cuerpo "{}" (un OPTIONS con cuerpo vacío
 * perdía los headers al pasar por el adaptador de Lambda).
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Component
public class AwsCorsFilter extends OncePerRequestFilter {

    @Value("${pedidos360.cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        List<String> allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .toList();

        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write("{}");
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
    }
}
