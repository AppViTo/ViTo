package com.vito.gateway.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T-B21: consulta de la clave revocada y comportamiento con Redis caído (falla abierto). */
class RedisRevokedAccessTokensTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RedisRevokedAccessTokens revoked = new RedisRevokedAccessTokens(redis, meters, TIMEOUT);

    private double errors() {
        return meters.counter(RedisRevokedAccessTokens.ERRORS_METRIC).count();
    }

    @Test
    void jtiWithKeyIsRevoked() {
        when(redis.hasKey("revoked_jti:jti-1")).thenReturn(Mono.just(true));

        assertTrue(revoked.isRevoked("jti-1").block());
        assertEquals(0, errors());
    }

    @Test
    void jtiWithoutKeyIsNotRevoked() {
        when(redis.hasKey("revoked_jti:jti-1")).thenReturn(Mono.just(false));

        assertFalse(revoked.isRevoked("jti-1").block());
    }

    @Test
    void redisDownFailsOpenAndCountsTheError() {
        when(redis.hasKey("revoked_jti:jti-1"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Connection refused")));

        assertFalse(revoked.isRevoked("jti-1").block());
        assertEquals(1, errors());
    }

    @Test
    void slowRedisFailsOpenAfterTimeout() {
        when(redis.hasKey("revoked_jti:jti-1")).thenReturn(Mono.never());

        assertFalse(revoked.isRevoked("jti-1").block(Duration.ofSeconds(2)));
        assertEquals(1, errors());
    }
}
