package com.vito.gateway.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los perfiles no deben redefinir la lista de rutas: si lo hacen, Spring la reemplaza entera y
 * se pierden RequestRateLimiter y CircuitBreaker de application.yml (T-B11).
 */
class RouteFiltersProfileTest {

    static RouteDefinition route(GatewayProperties properties, String id) {
        return properties.getRoutes().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta la ruta " + id));
    }

    static List<String> filterNames(RouteDefinition route) {
        return route.getFilters().stream().map(FilterDefinition::getName).toList();
    }

    static void assertProtectedRoutes(GatewayProperties properties, String authHost, String checkinHost) {
        RouteDefinition auth = route(properties, "auth-service");
        assertTrue(filterNames(auth).containsAll(List.of("RequestRateLimiter", "CircuitBreaker")),
                "auth-service sin filtros: " + filterNames(auth));
        assertEquals(URI.create("http://" + authHost + ":8081"), auth.getUri());

        assertTrue(filterNames(route(properties, "event-service")).contains("RequestRateLimiter"));
        assertEquals(URI.create("http://" + checkinHost + ":8084"), route(properties, "checkin-ws").getUri());
        assertRestRoutesRateLimited(properties);
    }

    /** CONVENTIONS §9 (T-B23): auth 10/20, events 20/40 y el resto de las rutas REST 10/20. */
    static void assertRestRoutesRateLimited(GatewayProperties properties) {
        List<RouteDefinition> rest = properties.getRoutes().stream()
                .filter(r -> r.getPredicates().stream()
                        .anyMatch(p -> p.getArgs().values().stream().anyMatch(v -> v.startsWith("/api/v1/"))))
                .toList();
        assertEquals(12, rest.size(), "rutas REST: " + rest.stream().map(RouteDefinition::getId).toList());

        for (RouteDefinition route : rest) {
            FilterDefinition limiter = route.getFilters().stream()
                    .filter(f -> f.getName().equals("RequestRateLimiter"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(route.getId() + " sin RequestRateLimiter"));
            boolean events = route.getId().equals("event-service");
            assertEquals(events ? "20" : "10", limiter.getArgs().get("redis-rate-limiter.replenishRate"), route.getId());
            assertEquals(events ? "40" : "20", limiter.getArgs().get("redis-rate-limiter.burstCapacity"), route.getId());
        }
    }

    @Nested
    @SpringBootTest(properties = "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long")
    @ActiveProfiles("dev")
    class DevProfile {
        @Autowired
        GatewayProperties properties;

        @Test
        void routesKeepRateLimiterAndCircuitBreaker() {
            assertProtectedRoutes(properties, "localhost", "localhost");
        }
    }

    @Nested
    @SpringBootTest(properties = "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long")
    @ActiveProfiles("docker")
    class DockerProfile {
        @Autowired
        GatewayProperties properties;

        @Test
        void routesKeepRateLimiterAndCircuitBreakerWithComposeHosts() {
            assertProtectedRoutes(properties, "auth-service", "checkin-service");
        }
    }

    /** Detrás de Caddy el bucket por IP sale de X-Forwarded-For (T-B23). */
    @Nested
    @SpringBootTest(properties = "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long")
    @ActiveProfiles({"prod", "docker"})
    class ProdProfile {
        @Autowired
        GatewayProperties properties;

        @Value("${vito.rate-limit.trusted-proxies}")
        int trustedProxies;

        @Test
        void routesKeepRateLimiterAndTrustCaddyAsTheOnlyProxy() {
            assertProtectedRoutes(properties, "auth-service", "checkin-service");
            assertEquals(1, trustedProxies);
        }
    }
}
