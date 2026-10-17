package com.vito.gateway.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
}
