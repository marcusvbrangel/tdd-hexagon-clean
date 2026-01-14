package com.mvbr.retailstore.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    // Client que contém as roles do ms-order no Keycloak (resource_access.{client}.roles)
    private static final String ORDER_CLIENT_ID = "ms-order-api";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth

                        // -------------------------
                        // PUBLIC (sem token) - se não existir nada público, remova esta seção
                        // -------------------------
                        .requestMatchers("/actuator/**").permitAll()

                        // -------------------------
                        // ORDERS (ms-order)
                        // -------------------------
                        // Criar pedido
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/**").hasRole("ORDER_CREATE")

                        // Listar/consultar pedidos
                        .requestMatchers(HttpMethod.GET,  "/api/v1/orders/**").hasRole("ORDER_LIST")

                        // Cancelar pedido (use a role certa; aqui mantive ORDER_CREATE por falta de ORDER_CANCEL)
                        //.requestMatchers(HttpMethod.POST, "/api/v1/orders/*/cancel").hasRole("ORDER_CREATE")

                        // -------------------------
                        // ADMIN (ms-order)
                        // -------------------------
                        .requestMatchers("/api/v1/admin/**").hasRole("ORDER_ADMIN")

                        // -------------------------
                        // DEFAULT (seguro)
                        // -------------------------
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthConverter()))
                );

        return http.build();
    }

    /**
     * Converte roles do Keycloak em authorities do Spring.
     *
     * - Filtra apenas roles do client "ms-order-api" (resource_access.ms-order-api.roles)
     * - Mantém realm roles (opcional) se você quiser usar em algum lugar
     * - Converte para "ROLE_<ROLE>" em uppercase para bater com hasRole("...").
     */
    private Converter<Jwt, JwtAuthenticationToken> keycloakJwtAuthConverter() {
        return jwt -> {
            Set<String> roles = new HashSet<>();

            // 1) realm_access.roles (opcional)
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof Collection<?> rr) {
                    rr.forEach(r -> roles.add(String.valueOf(r)));
                }
            }

            // 2) client roles: resource_access.ms-order-api.roles (recomendado)
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                Object clientObj = resourceAccess.get(ORDER_CLIENT_ID);
                if (clientObj instanceof Map<?, ?> clientMap) {
                    Object clientRolesObj = clientMap.get("roles");
                    if (clientRolesObj instanceof Collection<?> cr) {
                        cr.forEach(r -> roles.add(String.valueOf(r)));
                    }
                }
            }

            Set<GrantedAuthority> authorities = roles.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(r -> "ROLE_" + r.toUpperCase(Locale.ROOT))
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toUnmodifiableSet());

            // Principal estável: sub (UUID do usuário no Keycloak)
            String principal = jwt.getSubject();

            return new JwtAuthenticationToken(jwt, authorities, principal);
        };
    }
}
