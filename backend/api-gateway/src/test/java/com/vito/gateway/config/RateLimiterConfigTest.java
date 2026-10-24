package com.vito.gateway.config;

import com.vito.gateway.security.JwtAuthenticationFilter;
import com.vito.gateway.security.JwtAuthenticationFilterTest;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RateLimiterConfigTest {

    private static final String CADDY = "172.18.0.5";

    private final KeyResolver resolver = new RateLimiterConfig().userKeyResolver(0);
    private final KeyResolver behindCaddy = new RateLimiterConfig().userKeyResolver(1);

    private final JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
            JwtAuthenticationFilterTest.SECRET, (exchange, handler) -> Mono.empty(), jti -> Mono.just(false));

    private static MockServerWebExchange loginFrom(String ip, String spoofedUserId) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(ip, 50000))
                .header("X-User-Id", spoofedUserId));
    }

    /** Login reenviado por Caddy: la conexión viene de Caddy y X-Forwarded-For trae al cliente. */
    private static MockServerWebExchange loginViaCaddy(String forwardedFor) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(CADDY, 50000))
                .header("X-Forwarded-For", forwardedFor));
    }

    /** Clave del bucket tras pasar por el filtro JWT real, como en la cadena del gateway. */
    private String bucketAfterJwtFilter(String userId, String spoofedUserId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events/evt_1")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 50000))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtAuthenticationFilterTest.token(userId))
                .header("X-User-Id", spoofedUserId));
        AtomicReference<String> key = new AtomicReference<>();
        jwtFilter.filter(exchange, downstream -> resolver.resolve(downstream).doOnNext(key::set).then()).block();
        return key.get();
    }

    @Test
    void rotatingSpoofedUserIdDoesNotChangeTheBucket() {
        String first = resolver.resolve(loginFrom("203.0.113.7", "attempt-1")).block();
        String second = resolver.resolve(loginFrom("203.0.113.7", "attempt-2")).block();

        assertEquals("ip:203.0.113.7", first);
        assertEquals(first, second);
    }

    @Test
    void authenticatedRequestsAreKeyedByGatewayUser() {
        MockServerWebExchange exchange = loginFrom("203.0.113.7", "victim");
        exchange.getAttributes().put(JwtAuthenticationFilter.AUTHENTICATED_USER_ATTR, "usr_real");

        assertEquals("user:usr_real", resolver.resolve(exchange).block());
    }

    @Test
    void twoUsersFromTheSameIpGetDistinctBuckets() {
        String alice = bucketAfterJwtFilter("usr_alice", "usr_alice");
        String bob = bucketAfterJwtFilter("usr_bob", "usr_bob");

        assertEquals("user:usr_alice", alice);
        assertEquals("user:usr_bob", bob);
        assertNotEquals(alice, bob);
    }

    @Test
    void spoofedUserIdFromTheClientDoesNotChangeTheBucket() {
        assertEquals("user:usr_alice", bucketAfterJwtFilter("usr_alice", "usr_bob"));
        assertEquals("user:usr_alice", bucketAfterJwtFilter("usr_alice", "attempt-2"));
    }

    @Test
    void behindCaddyPublicRoutesAreKeyedByTheClientIp() {
        String first = behindCaddy.resolve(loginViaCaddy("198.51.100.9")).block();
        String second = behindCaddy.resolve(loginViaCaddy("198.51.100.10")).block();

        assertEquals("ip:198.51.100.9", first);
        assertEquals("ip:198.51.100.10", second);
    }

    @Test
    void behindCaddyOnlyTheHopAddedByCaddyCounts() {
        // Lo que el cliente antepone a X-Forwarded-For no rota el bucket
        assertEquals("ip:198.51.100.9", behindCaddy.resolve(loginViaCaddy("10.0.0.1, 198.51.100.9")).block());
    }

    @Test
    void withoutTrustedProxyTheClientForwardedForIsIgnored() {
        assertEquals("ip:" + CADDY, resolver.resolve(loginViaCaddy("198.51.100.9")).block());
    }
}
