package com.vito.gateway.controller;

import com.vito.gateway.error.ErrorResponseContract;
import com.vito.gateway.security.JwtAuthenticationFilterTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T025 (Ola 2a, research R9, CONVENTIONS §9): {@code searchUsers} se limita por usuario a 1/s con
 * ráfaga de 10, con las rutas reales de application.yml y Redis real. {@code getUsersByIds} va por
 * la ruta {@code users}, con el límite de auth, y no gasta el cupo de la búsqueda.
 */
@AutoConfigureMetrics
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsersSearchRateLimitIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    /** auth-service falso: dice qué ruta le llegó y de parte de quién. */
    private static final DisposableServer authService = HttpServer.create()
            .port(0)
            .route(routes -> routes
                    .get("/api/v1/users/search", (request, response) -> response.sendString(Mono.just(
                            "search id=" + request.requestHeaders().get("X-User-Id"))))
                    .get("/api/v1/users", (request, response) -> response.sendString(Mono.just(
                            "batch id=" + request.requestHeaders().get("X-User-Id")))))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("vito.security.revocation.redis-timeout", () -> "3s");
        // Solo cambia el destino: las rutas y sus filtros son los de application.yml
        registry.add("AUTH_SERVICE_URI", () -> "http://127.0.0.1:" + authService.port());
        registry.add("MANAGEMENT_PORT", () -> "0");
    }

    @AfterAll
    static void stopAuthService() {
        authService.disposeNow();
    }

    @LocalServerPort
    int port;

    @Autowired
    ReactiveRedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void emptyRateLimitBuckets() {
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block(TIMEOUT);
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).responseTimeout(TIMEOUT).build();
    }

    private EntityExchangeResult<String> search(String userId) {
        return client().get().uri("/api/v1/users/search?query=jose")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtAuthenticationFilterTest.token(userId))
                .exchange()
                .expectBody(String.class).returnResult();
    }

    @Test
    void searchIsForwardedToAuthServiceWithTheIdentity() {
        EntityExchangeResult<String> result = search("usr_ana");

        assertEquals(HttpStatus.OK, result.getStatus());
        assertEquals("search id=usr_ana", result.getResponseBody());
    }

    @Test
    void searchWithoutTokenIs401() {
        String body = client().get().uri("/api/v1/users/search?query=jose").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(String.class).returnResult().getResponseBody();

        ErrorResponseContract.assertMatches(body, 401, "UNAUTHORIZED");
    }

    @Test
    void getUsersByIdsWithoutTokenIs401() {
        String body = client().get().uri("/api/v1/users?ids=a,b").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(String.class).returnResult().getResponseBody();

        ErrorResponseContract.assertMatches(body, 401, "UNAUTHORIZED");
    }

    /** Ráfaga de 10: las 10 primeras pasan y después llega el 429 con Retry-After de 1 s. */
    @Test
    void searchIsLimitedPerUserAfterABurstOfTen() {
        List<EntityExchangeResult<String>> results = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            results.add(search("usr_ana"));
        }

        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.OK, results.get(i).getStatus(), "petición " + (i + 1));
        }
        List<EntityExchangeResult<String>> limited = results.subList(10, 15).stream()
                .filter(r -> r.getStatus().equals(HttpStatus.TOO_MANY_REQUESTS))
                .toList();
        // Se repone 1 por segundo: alguna de las 5 últimas podría pasar si la ráfaga tarda
        assertTrue(limited.size() >= 3, "429 entre las peticiones 11 y 15: " + limited.size());

        EntityExchangeResult<String> first = limited.get(0);
        assertEquals("1", first.getResponseHeaders().getFirst("Retry-After"));
        ErrorResponseContract.assertMatches(first.getResponseBody(), 429, "RATE_LIMITED");
    }

    @Test
    void anotherUserIsNotAffected() {
        for (int i = 0; i < 12; i++) {
            search("usr_ana");
        }

        EntityExchangeResult<String> other = search("usr_luis");

        assertEquals(HttpStatus.OK, other.getStatus());
        assertEquals("search id=usr_luis", other.getResponseBody());
    }

    @Test
    void getUsersByIdsDoesNotUseTheSearchBucket() {
        for (int i = 0; i < 12; i++) {
            search("usr_ana");
        }

        client().get().uri("/api/v1/users?ids=a,b")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtAuthenticationFilterTest.token("usr_ana"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("batch id=usr_ana");
    }
}
