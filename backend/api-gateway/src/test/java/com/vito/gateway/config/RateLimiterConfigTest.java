package com.vito.gateway.config;

import com.vito.gateway.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimiterConfigTest {

    private final KeyResolver resolver = new RateLimiterConfig().userKeyResolver();

    private static MockServerWebExchange loginFrom(String ip, String spoofedUserId) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(ip, 50000))
                .header("X-User-Id", spoofedUserId));
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
}
