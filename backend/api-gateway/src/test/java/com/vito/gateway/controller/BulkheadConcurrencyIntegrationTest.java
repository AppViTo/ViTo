package com.vito.gateway.controller;

import com.vito.gateway.security.JwtAuthenticationFilterTest;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-B37: bulkhead del filtro CircuitBreaker con la configuración real de application.yml.
 * Con el valor por defecto de Resilience4j (25), 60 peticiones simultáneas a una ruta recibían
 * 503 desde la número 26 y el breaker se abría.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BulkheadConcurrencyIntegrationTest {

    static final Duration BACKEND_DELAY = Duration.ofMillis(500);

    static final DisposableServer backend = slowBackend();

    /** Servicio de prueba que responde 200 a /slow/** tras {@link #BACKEND_DELAY}. */
    static DisposableServer slowBackend() {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/slow/**", (request, response) ->
                        Mono.delay(BACKEND_DELAY).then(response.status(200).sendString(Mono.just("ok")).then())))
                .bindNow();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        slowRoute(registry, backend.port(), "eventServiceCircuitBreaker");
    }

    /** Ruta /slow/** con el breaker de nombre {@code breaker}, igual que las de application.yml. */
    static void slowRoute(DynamicPropertyRegistry registry, int backendPort, String breaker) {
        String prefix = "spring.cloud.gateway.server.webflux.routes[0].";
        registry.add(prefix + "id", () -> "slow");
        registry.add(prefix + "uri", () -> "http://127.0.0.1:" + backendPort);
        registry.add(prefix + "predicates[0]", () -> "Path=/slow/**");
        registry.add(prefix + "filters[0].name", () -> "CircuitBreaker");
        registry.add(prefix + "filters[0].args.name", () -> breaker);
        registry.add(prefix + "filters[0].args.fallbackUri", () -> "forward:/fallback/events");
    }

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    /** {@code count} GET simultáneos al gateway; devuelve los códigos de estado. */
    static List<Integer> concurrentGets(int port, int count) {
        // El pool por defecto del cliente limita las conexiones simultáneas: uno propio, más grande
        ConnectionProvider pool = ConnectionProvider.builder("bulkhead-test").maxConnections(count).build();
        try {
            WebClient client = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create(pool)))
                    .baseUrl("http://127.0.0.1:" + port)
                    .defaultHeader("Authorization", "Bearer " + JwtAuthenticationFilterTest.token("usr_1"))
                    .build();
            return Flux.range(0, count)
                    .flatMap(i -> client.get().uri("/slow/" + i)
                            .exchangeToMono(response -> response.releaseBody()
                                    .thenReturn(response.statusCode().value())), count)
                    .collectList()
                    .block(Duration.ofSeconds(30));
        } finally {
            pool.disposeLater().block(Duration.ofSeconds(5));
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    BulkheadRegistry bulkheads;

    @Test
    void sixtyConcurrentCallsToOneRouteAllPass() {
        List<Integer> statuses = concurrentGets(port, 60);

        assertEquals(60, statuses.stream().filter(s -> s == 200).count(), "códigos: " + statuses);
        CircuitBreaker breaker = circuitBreakers.circuitBreaker("eventServiceCircuitBreaker");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void bulkheadDefaultAllowsTwoHundredConcurrentCalls() {
        assertEquals(200, bulkheads.getDefaultConfig().getMaxConcurrentCalls());
    }

    @Test
    void everyBreakerSharesTheCommonConfiguration() {
        BulkheadFullException full = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("x"));
        for (String name : List.of("eventServiceCircuitBreaker", "authServiceCircuitBreaker", "chatServiceCircuitBreaker")) {
            CircuitBreakerConfig config = circuitBreakers.circuitBreaker(name).getCircuitBreakerConfig();
            assertEquals(10, config.getSlidingWindowSize(), name);
            assertEquals(5, config.getMinimumNumberOfCalls(), name);
            assertEquals(50f, config.getFailureRateThreshold(), name);
            assertTrue(config.getIgnoreExceptionPredicate().test(full), name + " ignora BulkheadFullException");
        }
    }
}
