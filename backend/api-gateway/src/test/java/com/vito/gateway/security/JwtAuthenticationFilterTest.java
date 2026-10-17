package com.vito.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    static final String SECRET = "test-secret-key-for-unit-tests-only-256-bits-long";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    static String token(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", userId + "@vito.test")
                .claim("roles", List.of("USER"))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /** Ejecuta el filtro y devuelve el exchange que llegó a la cadena (null si no llegó). */
    private ServerWebExchange forwarded(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return ex.getResponse().setComplete();
        };
        filter.filter(exchange, chain).block();
        return captured.get();
    }

    @Test
    void rolesListFromAuthServiceBecomesCommaSeparatedHeader() {
        assertEquals("USER,ORGANIZER", JwtAuthenticationFilter.rolesHeader(List.of("USER", "ORGANIZER")));
    }

    @Test
    void singleRoleAndStringRolesArePassedThrough() {
        assertEquals("USER", JwtAuthenticationFilter.rolesHeader(List.of("USER")));
        assertEquals("USER,ADMIN", JwtAuthenticationFilter.rolesHeader("USER,ADMIN"));
    }

    @Test
    void missingRolesClaimBecomesEmptyHeader() {
        assertEquals("", JwtAuthenticationFilter.rolesHeader(null));
    }

    @Test
    void publicPathDropsSpoofedUserHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .header("X-User-Id", "victim")
                .header("X-User-Email", "victim@vito.test")
                .header("X-User-Roles", "ADMIN"));

        ServerWebExchange downstream = forwarded(exchange);

        HttpHeaders headers = downstream.getRequest().getHeaders();
        JwtAuthenticationFilter.USER_HEADERS.forEach(h -> assertNull(headers.get(h), h));
        assertNull(downstream.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ATTR));
    }

    @Test
    void authenticatedPathReplacesSpoofedHeadersWithTokenIdentity() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events/evt_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("usr_real"))
                .header("X-User-Id", "victim")
                .header("X-User-Roles", "ADMIN"));

        ServerWebExchange downstream = forwarded(exchange);

        HttpHeaders headers = downstream.getRequest().getHeaders();
        assertEquals(List.of("usr_real"), headers.get("X-User-Id"));
        assertEquals(List.of("usr_real@vito.test"), headers.get("X-User-Email"));
        assertEquals(List.of("USER"), headers.get("X-User-Roles"));
        assertEquals("usr_real", downstream.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ATTR));
    }

    @Test
    void protectedPathWithoutTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events/evt_1")
                .header("X-User-Id", "victim"));

        assertNull(forwarded(exchange));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
