package com.vito.gateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Consulta en Redis la clave {@code revoked_jti:<jti>} que escribe auth-service
 * ({@code RedisCacheAdapter.REVOKED_JTI_PREFIX}) al cerrar sesión.
 *
 * <p><b>Falla abierto:</b> si Redis no responde dentro de {@code timeout}, el token se trata
 * como no revocado. La firma y el vencimiento ya se verificaron antes de llegar aquí, así que
 * el riesgo queda acotado a un token robado y revocado que sigue valiendo hasta su {@code exp}
 * mientras dure la caída. Fallar cerrado convertiría una caída de Redis en un 401 para toda la
 * plataforma (y el refresh también depende de Redis), igual que el RequestRateLimiter, que
 * también deja pasar sin Redis. Cada fallo cuenta en {@code vito.gateway.revocation.check.errors}
 * para alertar.
 */
@Slf4j
@Component
public class RedisRevokedAccessTokens implements RevokedAccessTokens {

    /** Mismo prefijo que auth-service: si cambia allá, cambia aquí. */
    public static final String KEY_PREFIX = "revoked_jti:";

    static final String ERRORS_METRIC = "vito.gateway.revocation.check.errors";

    private final ReactiveStringRedisTemplate redis;
    private final Duration timeout;
    private final Counter errors;

    public RedisRevokedAccessTokens(ReactiveStringRedisTemplate redis,
                                    MeterRegistry meterRegistry,
                                    @Value("${vito.security.revocation.redis-timeout:250ms}") Duration timeout) {
        this.redis = redis;
        this.timeout = timeout;
        this.errors = Counter.builder(ERRORS_METRIC)
                .description("Consultas de revocación de access tokens que fallaron (Redis caído o lento)")
                .register(meterRegistry);
    }

    @Override
    public Mono<Boolean> isRevoked(String jti) {
        return redis.hasKey(KEY_PREFIX + jti)
                .map(Boolean.TRUE::equals)
                .defaultIfEmpty(false)
                .timeout(timeout)
                .onErrorResume(e -> {
                    errors.increment();
                    log.warn("Revocation check unavailable, accepting signed token: {}", e.toString());
                    return Mono.just(false);
                });
    }
}
