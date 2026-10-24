package com.vito.auth.infrastructure.adapter.outbound.cache;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-B21: la revocación se escribe en el Redis real con la clave que lee api-gateway y con
 * TTL igual a la vida restante del access token.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisCacheAdapterIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisCacheAdapter adapter;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        adapter = new RedisCacheAdapter(redis);
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    @Test
    void revokedJtiIsStoredWithGatewayKeyAndRemainingLifetime() {
        adapter.revokeAccessToken("jti-it-1", Duration.ofMinutes(10));

        // Mismo formato que RedisRevokedAccessTokens.KEY_PREFIX en api-gateway
        assertEquals("1", redis.opsForValue().get("revoked_jti:jti-it-1"));
        Long ttl = redis.getExpire("revoked_jti:jti-it-1", TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 590 && ttl <= 600, "TTL: " + ttl);
    }

    @Test
    void revokedJtiDisappearsWhenTheTokenWouldHaveExpired() throws InterruptedException {
        adapter.revokeAccessToken("jti-it-2", Duration.ofMillis(300));

        Thread.sleep(600);

        assertTrue(Boolean.FALSE.equals(redis.hasKey("revoked_jti:jti-it-2")));
    }
}
