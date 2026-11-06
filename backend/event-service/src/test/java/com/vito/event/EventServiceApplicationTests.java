package com.vito.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Spring Boot desactiva la exportación de métricas en tests; /actuator/prometheus la necesita
@AutoConfigureMetrics
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventServiceApplicationTests {

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

    /** T039 (R5): la vista previa es pública; un token mal formado responde el 404 único sin tocar la base. */
    @Test
    void theInviteLinkPreviewIsPublic() {
        ResponseEntity<String> response = http.getForEntity(url("/api/v1/invite-links/no-es-un-token"), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("El enlace no existe o ya no está disponible"));
    }

    /** T039 (R5): unirse por enlace exige sesión; la regla pública no la cubre. */
    @Test
    void acceptingAnInviteLinkRequiresASession() {
        ResponseEntity<String> response = http.postForEntity(
                url("/api/v1/invite-links/k3J9xQ2mV7pL1sT8wY4zBc/accept"), null, String.class);

        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403,
                "status " + response.getStatusCode());
    }

    /** T-B14: métricas en formato Prometheus para Grafana Cloud. */
    @Test
    void prometheusEndpointExposesJvmMetrics() {
        ResponseEntity<String> response = http.getForEntity(url("/actuator/prometheus"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("jvm_memory_used_bytes"));
    }
}
