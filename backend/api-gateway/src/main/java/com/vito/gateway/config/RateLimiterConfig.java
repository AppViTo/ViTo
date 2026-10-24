package com.vito.gateway.config;

import com.vito.gateway.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    /**
     * Usuario autenticado por el gateway; si no hay (rutas públicas como /auth/login), la IP.
     * Nunca usa X-User-Id: en rutas públicas lo controla el cliente y permitiría rotar el bucket.
     *
     * @param trustedProxies saltos de proxy delante del gateway (T-B23). En prod es 1: Caddy
     *                       reescribe X-Forwarded-For con la IP del cliente y, sin esto, todas las
     *                       IP compartirían el bucket de la de Caddy. Con 0 se usa la dirección de
     *                       la conexión y un X-Forwarded-For del cliente no cuenta.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver(@Value("${vito.rate-limit.trusted-proxies:0}") int trustedProxies) {
        RemoteAddressResolver addresses = trustedProxies > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxies)
                : new RemoteAddressResolver() { };
        return exchange -> {
            String userId = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ATTR);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            InetSocketAddress address = addresses.resolve(exchange);
            return Mono.just(address != null ? "ip:" + host(address) : "anonymous");
        };
    }

    /** XForwardedRemoteAddressResolver devuelve direcciones sin resolver, sin getAddress(). */
    private static String host(InetSocketAddress address) {
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
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
