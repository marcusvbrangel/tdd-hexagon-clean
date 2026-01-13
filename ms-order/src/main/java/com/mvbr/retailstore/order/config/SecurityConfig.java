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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth

                        // -------------------------
                        // PUBLIC (sem token)
                        // -------------------------
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()

                        // -------------------------
                        // ORDERS (ms-order)
                        // -------------------------
                        // Criar pedido
                        //.requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAnyRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").authenticated()

                        // Ler pedidos (exemplos - ajuste conforme seu design)
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasAnyRole("CUSTOMER", "ADMIN")

                        // Cancelar pedido (exemplo)
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/cancel").hasAnyRole("CUSTOMER")

                        // -------------------------
                        // ADMIN
                        // -------------------------
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

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

    private Converter<Jwt, JwtAuthenticationToken> keycloakJwtAuthConverter() {
        return jwt -> {
            Set<String> roles = new HashSet<>();

            // realm_access.roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof Collection<?> rr) {
                    rr.forEach(r -> roles.add(String.valueOf(r)));
                }
            }

            // (opcional) client roles: resource_access.{client}.roles
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                for (Object clientObj : resourceAccess.values()) {
                    if (clientObj instanceof Map<?, ?> clientMap) {
                        Object clientRolesObj = clientMap.get("roles");
                        if (clientRolesObj instanceof Collection<?> cr) {
                            cr.forEach(r -> roles.add(String.valueOf(r)));
                        }
                    }
                }
            }

            Set<GrantedAuthority> authorities = roles.stream()
                    .map(r -> "ROLE_" + r.toUpperCase(Locale.ROOT))
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toUnmodifiableSet());

            String principal = Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                    .orElse(jwt.getSubject());

            return new JwtAuthenticationToken(jwt, authorities, principal);
        };

    }

}
