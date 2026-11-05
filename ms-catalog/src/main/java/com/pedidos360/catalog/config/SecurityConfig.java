package com.pedidos360.catalog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.dto.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Seguridad del microservicio: valida el JWT emitido por Cognito (issuer, firma vía JWKS
 * y vigencia, configurado con spring.security.oauth2.resourceserver.jwt.issuer-uri).
 * Es la "segunda validación" tras el JWT Authorizer de API Gateway.
 *
 * Los roles (ADMIN/OPERADOR/CLIENTE) salen del claim "cognito:groups" del token, así que
 * los @PreAuthorize("hasRole(...)") de los controladores funcionan igual que en el monolito.
 * Cada microservicio valida el token por su cuenta: no confía en que otro ya lo hizo.
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AwsCorsFilter awsCorsFilter;

    public SecurityConfig(AwsCorsFilter awsCorsFilter) {
        this.awsCorsFilter = awsCorsFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .addFilterBefore(awsCorsFilter, DisableEncodeUrlFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "No autenticado: token ausente o inválido", request.getRequestURI()))
                )
                .exceptionHandling(eh -> eh
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción", request.getRequestURI()))
                );

        return http.build();
    }

    /**
     * - authorities: cada valor de "cognito:groups" se transforma en ROLE_<GRUPO>.
     * - nombre del principal: claim "cognito:username" (no el "sub", que es un UUID).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extraerRoles);
        converter.setPrincipalClaimName("cognito:username");
        return converter;
    }

    private Collection<GrantedAuthority> extraerRoles(Jwt jwt) {
        List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
        if (grupos == null) {
            return List.of();
        }
        return grupos.stream()
                .map(grupo -> new SimpleGrantedAuthority("ROLE_" + grupo.toUpperCase()))
                .collect(Collectors.toList());
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
