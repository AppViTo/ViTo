package com.vito.gateway.error;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * El filtro RequestRateLimiter responde 429 con {@code setComplete()} y sin cuerpo. Este
 * decorador cambia ese cierre vacío por el envelope de ADR-003 ({@code RATE_LIMITED}).
 * Un 429 que venga del servicio con cuerpo propio pasa por {@code writeWith} y no se toca.
 */
@Component
public class RateLimitedResponseFilter implements GlobalFilter, Ordered {

    static final String MESSAGE = "Has superado el límite de peticiones. Reintenta en unos segundos";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> setComplete() {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) && !isCommitted()) {
                    return ErrorResponses.write(getDelegate(), HttpStatus.TOO_MANY_REQUESTS,
                            ErrorResponses.RATE_LIMITED, MESSAGE);
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // Antes que JwtAuthenticationFilter (-1): toda la cadena recibe la respuesta decorada
        return -2;
    }
}
