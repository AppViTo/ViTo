package com.vito.gateway.security;

import com.vito.gateway.error.ErrorResponseContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-B35 (T017–T019) de punta a punta con las rutas reales de application.yml y Redis real:
 * la vista previa llega a event-service sin sesión, unirse exige token y recibe
 * {@code X-User-Name}, y la vista previa se corta por IP con 429 + Retry-After.
 */
// /actuator/prometheus necesita la exportación de métricas, apagada por defecto en tests
@AutoConfigureMetrics
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InviteLinkRoutesIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String TOKEN = "k3J9xQ2mV7pL1sT8wY4zBc";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    /** event-service falso: responde con la identidad que le puso el gateway. */
    private static final DisposableServer eventService = HttpServer.create()
            .port(0)
            .route(routes -> routes
                    .get("/api/v1/invite-links/{token}", (request, response) -> response.sendString(Mono.just(
                            "preview id=" + request.requestHeaders().get("X-User-Id"))))
                    .post("/api/v1/invite-links/{token}/accept", (request, response) -> response.sendString(Mono.just(
                            "accept id=" + request.requestHeaders().get("X-User-Id")
                                    + " name=" + request.requestHeaders().get("X-User-Name")))))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("vito.security.revocation.redis-timeout", () -> "3s");
        // Solo cambia el destino: las rutas y sus filtros son los de application.yml
        registry.add("EVENT_SERVICE_URI", () -> "http://127.0.0.1:" + eventService.port());
        registry.add("MANAGEMENT_PORT", () -> "0");
    }

    @AfterAll
    static void stopEventService() {
        eventService.disposeNow();
    }

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Autowired
    ReactiveRedisConnectionFactory redisConnectionFactory;

    private WebTestClient client(int targetPort) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + targetPort).responseTimeout(TIMEOUT).build();
    }

    private void emptyRateLimitBuckets() {
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block(TIMEOUT);
    }

    @Test
    void previewIsForwardedWithoutSessionAndWithoutIdentity() {
        emptyRateLimitBuckets();
        client(port).get().uri("/api/v1/invite-links/" + TOKEN)
                .header("X-User-Id", "victim")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("preview id=null");
    }

    @Test
    void acceptWithoutTokenIs401AndWithTokenCarriesTheName() {
        emptyRateLimitBuckets();
        String body = client(port).post().uri("/api/v1/invite-links/" + TOKEN + "/accept")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(String.class).returnResult().getResponseBody();
        ErrorResponseContract.assertMatches(body, 401, "UNAUTHORIZED");

        client(port).post().uri("/api/v1/invite-links/" + TOKEN + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtAuthenticationFilterTest.tokenWithName("usr_ana", "Ana"))
                .header("X-User-Name", "Suplantado")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("accept id=usr_ana name=Ana");
    }

    /** 10/min por IP con ráfaga de 20: la petición 21 recibe 429 con Retry-After de 6 s. */
    @Test
    void previewIsRateLimitedPerIpWithRetryAfter() {
        emptyRateLimitBuckets();
        for (int i = 1; i <= 20; i++) {
            client(port).get().uri("/api/v1/invite-links/" + TOKEN).exchange()
                    .expectStatus().isOk();
        }

        EntityExchangeResult<String> limited = client(port).get().uri("/api/v1/invite-links/" + TOKEN).exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody(String.class).returnResult();

        assertEquals("6", limited.getResponseHeaders().getFirst("Retry-After"));
        ErrorResponseContract.assertMatches(limited.getResponseBody(), 429, "RATE_LIMITED");
        assertTrue(limited.getResponseBody().contains("Reintenta en 6 segundos"), limited.getResponseBody());

        // El panel de 429 por ruta (T062) se arma con esta métrica
        String metrics = client(managementPort).get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertTrue(metrics.lines().anyMatch(l -> l.startsWith("spring_cloud_gateway_requests_seconds_count")
                        && l.contains("routeId=\"invite-link-preview\"") && l.contains("httpStatusCode=\"429\"")),
                "falta spring_cloud_gateway_requests con routeId y 429");
        assertTrue(!metrics.contains(TOKEN), "el token no aparece en las métricas");
    }
}
