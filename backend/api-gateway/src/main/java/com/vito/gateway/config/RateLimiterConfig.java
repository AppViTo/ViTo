package com.vito.gateway.config;

import com.vito.gateway.security.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Usuario autenticado por el gateway; si no hay (rutas públicas como /auth/login), la IP.
     * Nunca usa X-User-Id: en rutas públicas lo controla el cliente y permitiría rotar el bucket.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ATTR);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            return Mono.just(exchange.getRequest().getRemoteAddress() != null
                    ? "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "anonymous");
        };
    }

    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getHeaders().getFirst("X-API-Key") != null
                        ? exchange.getRequest().getHeaders().getFirst("X-API-Key")
                        : "no-api-key"
        );
    }
}
