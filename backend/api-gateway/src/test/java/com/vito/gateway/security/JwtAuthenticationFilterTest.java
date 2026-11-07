package com.vito.gateway.security;

import com.vito.gateway.error.ErrorResponseContract;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JwtAuthenticationFilterTest {

    public static final String SECRET = "test-secret-key-for-unit-tests-only-256-bits-long";

    /** Registra el handler con el que el filtro respondería un handshake rechazado. */
    private final AtomicReference<WebSocketHandler> rejectedHandshake = new AtomicReference<>();

    private final WebSocketService webSocketService = (exchange, handler) -> {
        rejectedHandshake.set(handler);
        return Mono.empty();
    };

    /** jti revocados y jti consultados por el filtro (T-B21). */
    private final Set<String> revokedJtis = new HashSet<>();
    private final List<String> consultedJtis = new ArrayList<>();

    private final RevokedAccessTokens revokedAccessTokens = jti -> {
        consultedJtis.add(jti);
        return Mono.just(revokedJtis.contains(jti));
    };

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(SECRET, webSocketService, revokedAccessTokens);

    /** Token sin jti, como los emitidos antes de T-B21. */
    public static String token(String userId) {
        return token(userId, null);
    }

    /** Token como los de auth-service desde T-B21: con jti y vencimiento. */
    public static String token(String userId, String jti) {
        return Jwts.builder()
                .id(jti)
                .subject(userId)
                .claim("email", userId + "@vito.test")
                .claim("roles", List.of("USER"))
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
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
        assertUnauthorizedEnvelope(exchange, JwtAuthenticationFilter.MISSING_TOKEN_MESSAGE);
    }

    @Test
    void protectedPathWithInvalidTokenGetsUnauthorizedEnvelope() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"));

        assertNull(forwarded(exchange));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertUnauthorizedEnvelope(exchange, JwtAuthenticationFilter.INVALID_TOKEN_MESSAGE);
    }

    /** ADR-003: el 401 del gateway es el mismo envelope que el de los servicios. */
    private static void assertUnauthorizedEnvelope(MockServerWebExchange exchange, String message) {
        String body = exchange.getResponse().getBodyAsString().block();
        ErrorResponseContract.assertMatches(body, 401, "UNAUTHORIZED");
        assertEquals(message, ErrorResponseContract.parse(body).get("message"));
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
    void tokenWithRevokedJtiGetsUnauthorizedEnvelope() {
        revokedJtis.add("jti-revoked");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("usr_real", "jti-revoked")));

        assertNull(forwarded(exchange));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertUnauthorizedEnvelope(exchange, JwtAuthenticationFilter.INVALID_TOKEN_MESSAGE);
        assertNull(exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ATTR));
    }

    @Test
    void tokenWithActiveJtiIsForwarded() {
        revokedJtis.add("jti-other");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events/evt_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("usr_real", "jti-active")));

        ServerWebExchange downstream = forwarded(exchange);

        assertNotNull(downstream);
        assertEquals(List.of("usr_real"), downstream.getRequest().getHeaders().get("X-User-Id"));
        assertEquals(List.of("jti-active"), consultedJtis);
    }

    @Test
    void tokenWithoutJtiIsNotLookedUp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events/evt_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("usr_real")));

        assertNotNull(forwarded(exchange));
        assertEquals(List.of(), consultedJtis);
    }

    @Test
    void forgedTokenIsRejectedBeforeLookingUpRevocation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events/evt_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"));

        assertNull(forwarded(exchange));
        assertEquals(List.of(), consultedJtis);
    }

    @Test
    void webSocketHandshakeWithRevokedTokenIsClosedWith4401() {
        revokedJtis.add("jti-revoked");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                handshake("/ws/chat/evt_1?token=" + token("usr_real", "jti-revoked")));

        assertNull(forwarded(exchange));

        WebSocketHandler handler = rejectedHandshake.get();
        assertNotNull(handler, "el handshake debe aceptarse para poder cerrar con 4401");
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.close(any())).thenReturn(Mono.empty());
        handler.handle(session).block();
        verify(session).close(JwtAuthenticationFilter.WS_UNAUTHORIZED);
    }

    @Test
    void plainHttpRequestToWebSocketPathWithoutTokenGets401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws/chat"));

        assertNull(forwarded(exchange));
        assertNull(rejectedHandshake.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertUnauthorizedEnvelope(exchange, JwtAuthenticationFilter.MISSING_TOKEN_MESSAGE);
    }

    // ---- Ola 1: rutas públicas por método y patrón exacto (T018, R5) ----------------------

    /** Token como los de auth-service desde T-B35: con el claim {@code name} (R6). */
    static String tokenWithName(String userId, String name) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", userId + "@vito.test")
                .claim("roles", List.of("USER"))
                .claim("name", name)
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockServerWebExchange anonymous(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header("X-User-Id", "victim").header("X-User-Name", "Suplantado"));
    }

    @Test
    void inviteLinkPreviewIsPublicAndCarriesNoIdentity() {
        ServerWebExchange downstream = forwarded(anonymous(HttpMethod.GET, "/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc"));

        assertNotNull(downstream, "la vista previa no exige sesión");
        JwtAuthenticationFilter.USER_HEADERS.forEach(h -> assertNull(downstream.getRequest().getHeaders().get(h), h));
    }

    @Test
    void acceptingAnInviteLinkWithoutTokenIs401() {
        MockServerWebExchange exchange = anonymous(HttpMethod.POST, "/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc/accept");

        assertNull(forwarded(exchange), "accept no es pública");
        assertUnauthorizedEnvelope(exchange, JwtAuthenticationFilter.MISSING_TOKEN_MESSAGE);
    }

    @Test
    void onlyTheExactMethodAndPatternArePublic() {
        String[][] protectedRequests = {
                {"GET", "/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc/accept"},
                {"POST", "/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc"},
                {"DELETE", "/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc"},
                {"GET", "/api/v1/invite-links"},
                {"GET", "/api/v1/auth/login"},
                {"POST", "/api/v1/auth/login/extra"},
                {"POST", "/api/v1/auth/loginx"},
                {"POST", "/api/v1/auth/logout"},
                {"GET", "/api/v1/auth/me"},
        };
        for (String[] r : protectedRequests) {
            MockServerWebExchange exchange = anonymous(HttpMethod.valueOf(r[0]), r[1]);
            assertNull(forwarded(exchange), r[0] + " " + r[1] + " no debe ser pública");
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(), r[0] + " " + r[1]);
        }
    }

    @Test
    void loginRegisterAndRefreshRemainPublic() {
        for (String path : new String[]{"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh"}) {
            assertNotNull(forwarded(anonymous(HttpMethod.POST, path)), path);
        }
    }

    // ---- X-User-Name (T017, R6) ----------------------------------------------------------

    @Test
    void userNameComesFromTheClaimAndNeverFromTheClient() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithName("usr_real", "José Begoña"))
                .header("X-User-Name", "Suplantado"));

        HttpHeaders headers = forwarded(exchange).getRequest().getHeaders();

        assertEquals(List.of("José Begoña"), headers.get("X-User-Name"));
        assertEquals(List.of("usr_real"), headers.get("X-User-Id"));
    }

    @Test
    void tokenWithoutNameDropsTheClientHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("usr_real"))
                .header("X-User-Name", "Suplantado"));

        assertNull(forwarded(exchange).getRequest().getHeaders().get("X-User-Name"));
    }

    @Test
    void nameHeaderIsTrimmedCappedAndOnlyLatin1() {
        assertEquals("Alex Villa", JwtAuthenticationFilter.nameHeader("  Alex Villa  "));
        assertEquals(100, JwtAuthenticationFilter.nameHeader("a".repeat(150)).length());
        assertNull(JwtAuthenticationFilter.nameHeader("   "));
        assertNull(JwtAuthenticationFilter.nameHeader(null));
        assertNull(JwtAuthenticationFilter.nameHeader(42));
        assertNull(JwtAuthenticationFilter.nameHeader("Ana\r\nX-User-Id: victim"), "sin saltos de línea");
        assertNull(JwtAuthenticationFilter.nameHeader("Ana 🎉"), "fuera de ISO-8859-1 no se envía");
        assertTrue(JwtAuthenticationFilter.USER_HEADERS.contains("X-User-Name"));
    }

    @Test
    void rejectedTokenLogNeverCarriesTheInviteToken() {
        assertEquals("/api/v1/invite-links/{token}/accept",
                InviteLinkTokenRedaction.redact("/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc/accept"));
    }
}
