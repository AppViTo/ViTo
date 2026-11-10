package com.vito.gateway.controller;

import com.vito.gateway.security.JwtAuthenticationFilterTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.netty.DisposableServer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-B37: con el bulkhead lleno (aquí reducido a 5) las llamadas de más reciben el fallback 503,
 * pero el breaker no las cuenta como fallas y sigue cerrado para las que sí caben.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BulkheadFullIntegrationTest {

    static final DisposableServer backend = BulkheadConcurrencyIntegrationTest.slowBackend();

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("resilience4j.bulkhead.configs.default.max-concurrent-calls", () -> 5);
        BulkheadConcurrencyIntegrationTest.slowRoute(registry, backend.port(), "eventServiceCircuitBreaker");
    }

    @LocalServerPort
    int port;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Test
    void rejectedCallsDoNotOpenTheBreaker() {
        List<Integer> statuses = BulkheadConcurrencyIntegrationTest.concurrentGets(port, 20);

        assertEquals(5, statuses.stream().filter(s -> s == 200).count(), "códigos: " + statuses);
        assertEquals(15, statuses.stream().filter(s -> s == 503).count(), "códigos: " + statuses);
        CircuitBreaker breaker = circuitBreakers.circuitBreaker("eventServiceCircuitBreaker");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getMetrics().getNumberOfFailedCalls());

        // Con el breaker cerrado, la siguiente ronda vuelve a pasar
        List<Integer> next = BulkheadConcurrencyIntegrationTest.concurrentGets(port, 5);
        assertEquals(List.of(200, 200, 200, 200, 200), next);
    }
}
