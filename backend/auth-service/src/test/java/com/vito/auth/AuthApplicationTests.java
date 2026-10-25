package com.vito.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Spring Boot desactiva la exportación de métricas en tests; /actuator/prometheus la necesita
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthApplicationTests {

    @Test
    void contextLoads() {
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** T-B14: probes que usan los healthchecks de Compose (no dependen de Mongo/Redis/Kafka). */
    @Test
    void livenessAndReadinessProbesAreUp() {
        for (String probe : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            ResponseEntity<String> response = http.getForEntity(url(probe), String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), probe);
            assertTrue(response.getBody().contains("\"UP\""), probe);
        }
    }

    /** T-B14: métricas en formato Prometheus para Grafana Cloud. */
    @Test
    void prometheusEndpointExposesJvmMetrics() {
        ResponseEntity<String> response = http.getForEntity(url("/actuator/prometheus"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("jvm_memory_used_bytes"));
    }
}
