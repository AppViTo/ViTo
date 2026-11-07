package com.vito.gateway.error;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
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

    static final String RETRY_AFTER = "Retry-After";
    static final String REQUESTED_TOKENS = "X-RateLimit-Requested-Tokens";
    static final String REPLENISH_RATE = "X-RateLimit-Replenish-Rate";

    /** "Has superado el límite de peticiones. Reintenta en N segundo(s)" (fixtures {@code *.429.json}). */
    static String message(long seconds) {
        return "Has superado el límite de peticiones. Reintenta en " + seconds + (seconds == 1 ? " segundo" : " segundos");
    }

    /**
     * Segundos hasta que el bucket repone lo que cuesta una petición: tokens por petición /
     * reposición por segundo, redondeado hacia arriba (CONVENTIONS §9). Sin los headers del
     * limitador, 1.
     */
    static long retryAfterSeconds(HttpHeaders headers) {
        try {
            long requested = Long.parseLong(headers.getFirst(REQUESTED_TOKENS));
            long replenish = Long.parseLong(headers.getFirst(REPLENISH_RATE));
            if (requested > 0 && replenish > 0) {
                return Math.max(1, (requested + replenish - 1) / replenish);
            }
        } catch (NumberFormatException e) {
            // header ausente (null) o con otro formato
        }
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> setComplete() {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) && !isCommitted()) {
                    long seconds = retryAfterSeconds(getHeaders());
                    getHeaders().set(RETRY_AFTER, String.valueOf(seconds));
                    return ErrorResponses.write(getDelegate(), HttpStatus.TOO_MANY_REQUESTS,
                            ErrorResponses.RATE_LIMITED, message(seconds));
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
