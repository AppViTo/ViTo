package com.vito.gateway.security;

import com.vito.gateway.error.ErrorResponseContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T-B21 de punta a punta en el gateway con Redis real: el token cuyo {@code jti} auth-service
 * revocó al hacer logout ({@code revoked_jti:<jti>} con TTL) recibe 401 con el envelope de
 * ADR-003 por HTTP y cierre 4401 por WebSocket; el mismo token sin revocar llega al servicio.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenRevocationIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    /** Servicio falso: responde con la identidad que le puso el gateway. */
    private static final DisposableServer backend = HttpServer.create()
            .port(0)
            .route(routes -> routes
                    .get("/api/v1/auth/me", (request, response) ->
                            response.sendString(Mono.just(String.valueOf(request.requestHeaders().get("X-User-Id")))))
                    .get("/ws/chat/{roomId}", (request, response) ->
                            response.sendWebsocket((in, out) -> out.sendString(Mono.just(
                                    String.valueOf(request.requestHeaders().get("X-User-Id")))))))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Margen para la primera conexión en un runner frío; el fallo abierto se prueba aparte
        registry.add("vito.security.revocation.redis-timeout", () -> "3s");
        registry.add("spring.cloud.gateway.routes[0].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + backend.port());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/v1/auth/**");
        registry.add("spring.cloud.gateway.routes[1].id", () -> "chat-ws");
        registry.add("spring.cloud.gateway.routes[1].uri", () -> "http://localhost:" + backend.port());
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/ws/chat/**");
    }

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    @LocalServerPort
    int port;

    @Autowired
    ReactiveStringRedisTemplate redis;

    private final ReactorNettyWebSocketClient wsClient = new ReactorNettyWebSocketClient();

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(TIMEOUT).build();
    }

    /** Lo mismo que escribe auth-service en logout (RedisCacheAdapter.revokeAccessToken). */
    private void revoke(String jti) {
        redis.opsForValue().set(RedisRevokedAccessTokens.KEY_PREFIX + jti, "1", Duration.ofMinutes(15)).block(TIMEOUT);
    }

    @Test
    void accessTokenUsedAfterLogoutGets401Envelope() {
        String jti = UUID.randomUUID().toString();
        String token = JwtAuthenticationFilterTest.token("usr_real", jti);

        client().get().uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("usr_real");

        revoke(jti);

        String body = client().get().uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(String.class).returnResult().getResponseBody();
        ErrorResponseContract.assertMatches(body, 401, "UNAUTHORIZED");
    }

    @Test
    void webSocketWithTokenRevokedAtLogoutIsClosedWith4401() {
        String jti = UUID.randomUUID().toString();
        String token = JwtAuthenticationFilterTest.token("usr_real", jti);
        URI uri = URI.create("ws://localhost:" + port + "/ws/chat/room_1?token=" + token);

        AtomicReference<String> reply = new AtomicReference<>();
        wsClient.execute(uri, session -> session.receive()
                        .take(1)
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(reply::set)
                        .then())
                .block(TIMEOUT);
        assertEquals("usr_real", reply.get());

        revoke(jti);

        AtomicReference<CloseStatus> status = new AtomicReference<>();
        wsClient.execute(uri, session -> session.closeStatus()
                        .doOnNext(status::set)
                        .and(session.receive().then()))
                .block(TIMEOUT);
        assertNotNull(status.get());
        assertEquals(4401, status.get().getCode());
    }
}
