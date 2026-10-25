package com.vito.gateway.controller;

import com.vito.gateway.error.ErrorResponseContract;
import com.vito.gateway.security.JwtAuthenticationFilterTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CircuitBreaker real del gateway (T-B02): POST lento que el servicio completa y POST a un
 * servicio caído. Usa la configuración de resilience4j de application.yml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "20s")
class CircuitBreakerFallbackIntegrationTest {

    /** Más que el TimeLimiter por defecto de Resilience4j (1 s), menos que el configurado. */
    private static final Duration SLOW_RESPONSE = Duration.ofSeconds(2);

    private static final DisposableServer backend = HttpServer.create()
            .port(0)
            .route(routes -> routes.post("/slow/register", (request, response) ->
                    Mono.delay(SLOW_RESPONSE).then(response.status(201)
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just("{\"created\":true}")).then())))
            .bindNow();

    private static final int closedPort = freePort();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        route(registry, 0, "slow", "http://127.0.0.1:" + backend.port(), "/slow/**", "auth");
        route(registry, 1, "down", "http://127.0.0.1:" + closedPort, "/down/**", "events");
    }

    private static void route(DynamicPropertyRegistry registry, int i, String id, String uri, String path,
                              String fallback) {
        String prefix = "spring.cloud.gateway.routes[" + i + "].";
        registry.add(prefix + "id", () -> id);
        registry.add(prefix + "uri", () -> uri);
        registry.add(prefix + "predicates[0]", () -> "Path=" + path);
        registry.add(prefix + "filters[0].name", () -> "CircuitBreaker");
        registry.add(prefix + "filters[0].args.name", () -> id + "CircuitBreaker");
        registry.add(prefix + "filters[0].args.fallbackUri", () -> "forward:/fallback/" + fallback);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    @Autowired
    WebTestClient client;

    private WebTestClient.RequestBodySpec post(String uri) {
        return client.post().uri(uri)
                .header("Authorization", "Bearer " + JwtAuthenticationFilterTest.token("usr_1"));
    }

    @Test
    void slowServiceResponseIsNotReplacedByFallback() {
        post("/slow/register").exchange()
                .expectStatus().isCreated()
                .expectBody(String.class).isEqualTo("{\"created\":true}");
    }

    @Test
    void postToUnavailableServiceGetsServiceUnavailableEnvelope() {
        String body = post("/down/events").exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class).returnResult().getResponseBody();

        ErrorResponseContract.assertMatches(body, 503, "SERVICE_UNAVAILABLE");
        assertEquals("El servicio de eventos no está disponible en este momento. Inténtalo de nuevo en unos segundos",
                ErrorResponseContract.parse(body).get("message"));
    }

    @Test
    void fallbackAcceptsEveryMethod() {
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH}) {
            String body = client.method(method).uri("/fallback/unknown").exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(String.class).returnResult().getResponseBody();
            ErrorResponseContract.assertMatches(body, 503, "SERVICE_UNAVAILABLE");
        }
    }
}
