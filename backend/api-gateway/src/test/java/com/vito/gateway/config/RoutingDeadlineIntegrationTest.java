package com.vito.gateway.config;

import com.vito.gateway.error.ErrorResponseContract;
import com.vito.gateway.security.JwtAuthenticationFilterTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-B37 (punto 4): una petición que se queda en un filtro anterior al enrutado recibe 503 con el
 * envelope al vencer el plazo, en vez de colgar hasta el timeout del cliente; una que ya llegó al
 * servicio no se corta aunque tarde más que el plazo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "20s")
class RoutingDeadlineIntegrationTest {

    static final Duration DEADLINE = Duration.ofMillis(500);

    /** Más que el plazo y menos que el TimeLimiter del breaker (10 s). */
    static final Duration SLOW_RESPONSE = Duration.ofSeconds(2);

    static final DisposableServer backend = HttpServer.create()
            .port(0)
            .route(routes -> routes
                    .get("/slow/**", (request, response) -> Mono.delay(SLOW_RESPONSE)
                            .then(response.status(200).sendString(Mono.just("ok")).then()))
                    .get("/hang/**", (request, response) -> response.status(200).sendString(Mono.just("ok"))))
            .bindNow();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("vito.gateway.routing-deadline", DEADLINE::toString);
        route(registry, 0, "slow", "/slow/**");
        route(registry, 1, "hang", "/hang/**");
    }

    private static void route(DynamicPropertyRegistry registry, int i, String id, String path) {
        String prefix = "spring.cloud.gateway.server.webflux.routes[" + i + "].";
        registry.add(prefix + "id", () -> id);
        registry.add(prefix + "uri", () -> "http://127.0.0.1:" + backend.port());
        registry.add(prefix + "predicates[0]", () -> "Path=" + path);
        registry.add(prefix + "filters[0].name", () -> "CircuitBreaker");
        registry.add(prefix + "filters[0].args.name", () -> id + "CircuitBreaker");
        registry.add(prefix + "filters[0].args.fallbackUri", () -> "forward:/fallback/events");
    }

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    /** Simula el filtro global que no completó en k6: nunca emite para /hang/**. */
    @TestConfiguration
    static class HangingFilterConfig {
        @Bean
        HangingFilter hangingFilter() {
            return new HangingFilter();
        }
    }

    /** Después de JwtAuthenticationFilter (-1) y antes de los filtros de la ruta. */
    static class HangingFilter implements GlobalFilter, Ordered {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            return exchange.getRequest().getURI().getPath().startsWith("/hang/")
                    ? Mono.never()
                    : chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    @Autowired
    WebTestClient client;

    @Autowired
    MeterRegistry meterRegistry;

    private WebTestClient.RequestHeadersSpec<?> get(String uri) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + JwtAuthenticationFilterTest.token("usr_1"));
    }

    @Test
    void requestStuckBeforeRoutingGetsServiceUnavailableAtTheDeadline() {
        double before = meterRegistry.counter(RoutingDeadlineFilter.METRIC).count();
        long start = System.nanoTime();

        String body = get("/hang/x").exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class).returnResult().getResponseBody();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        ErrorResponseContract.assertMatches(body, 503, "SERVICE_UNAVAILABLE");
        assertEquals(RoutingDeadlineFilter.MESSAGE, ErrorResponseContract.parse(body).get("message"));
        assertTrue(elapsed.compareTo(DEADLINE) >= 0 && elapsed.compareTo(Duration.ofSeconds(5)) < 0,
                "respondió en " + elapsed);
        assertEquals(before + 1, meterRegistry.counter(RoutingDeadlineFilter.METRIC).count());
    }

    @Test
    void routedRequestSlowerThanTheDeadlineIsNotCut() {
        double before = meterRegistry.counter(RoutingDeadlineFilter.METRIC).count();

        get("/slow/x").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");

        assertEquals(before, meterRegistry.counter(RoutingDeadlineFilter.METRIC).count());
    }
}
