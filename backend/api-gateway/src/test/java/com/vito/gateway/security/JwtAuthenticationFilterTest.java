package com.vito.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    static final String SECRET = "test-secret-key-for-unit-tests-only-256-bits-long";

    /** Registra el handler con el que el filtro respondería un handshake rechazado. */
    private final AtomicReference<WebSocketHandler> rejectedHandshake = new AtomicReference<>();

    private final WebSocketService webSocketService = (exchange, handler) -> {
        rejectedHandshake.set(handler);
        return Mono.empty();
    };

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET, webSocketService);

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

    private static MockServerHttpRequest.BaseBuilder<?> handshake(String uri) {
        return MockServerHttpRequest.get(uri)
                .header(HttpHeaders.UPGRADE, "websocket")
                .header(HttpHeaders.CONNECTION, "Upgrade");
    }

    @Test
    void webSocketHandshakeWithValidQueryTokenIsForwardedWithIdentityAndWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                handshake("/ws/checkin/evt_1?token=" + token("usr_real") + "&lang=es")
                        .header("X-User-Id", "victim"));

        ServerWebExchange downstream = forwarded(exchange);

        assertNotNull(downstream);
        assertEquals(List.of("usr_real"), downstream.getRequest().getHeaders().get("X-User-Id"));
        assertNull(downstream.getRequest().getQueryParams().getFirst("token"));
        assertEquals("es", downstream.getRequest().getQueryParams().getFirst("lang"));
        assertEquals("/ws/checkin/evt_1", downstream.getRequest().getURI().getPath());
        assertNull(rejectedHandshake.get());
    }

    @Test
    void webSocketHandshakeWithInvalidTokenIsClosedWith4401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(handshake("/ws/mosaic/evt_1?token=not-a-jwt"));

        assertNull(forwarded(exchange));

        WebSocketHandler handler = rejectedHandshake.get();
        assertNotNull(handler, "el handshake debe aceptarse para poder cerrar con 4401");
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.close(any())).thenReturn(Mono.empty());
        handler.handle(session).block();
        verify(session).close(JwtAuthenticationFilter.WS_UNAUTHORIZED);
        assertEquals(4401, JwtAuthenticationFilter.WS_UNAUTHORIZED.getCode());
    }

    @Test
    void webSocketHandshakeWithoutTokenIsClosedWith4401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(handshake("/ws/streaming/str_1"));

        assertNull(forwarded(exchange));
        assertNotNull(rejectedHandshake.get());
    }

    @Test
    void plainHttpRequestToWebSocketPathWithoutTokenGets401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws/chat"));

        assertNull(forwarded(exchange));
        assertNull(rejectedHandshake.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
