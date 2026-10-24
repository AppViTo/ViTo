package com.vito.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * T-B14: Actuator vive en un puerto propio (no publicado); el puerto público del gateway no
 * lo sirve y el endpoint "gateway" (que permite escribir rutas) está deshabilitado.
 */
// Spring Boot desactiva la exportación de métricas en tests; /actuator/prometheus la necesita
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MANAGEMENT_PORT=0"
})
class ActuatorEndpointsTest {

    @LocalServerPort
    int publicPort;

    @LocalManagementPort
    int managementPort;

    private WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test
    void probesAndPrometheusAreServedOnManagementPort() {
        WebTestClient management = client(managementPort);
        management.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        management.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
        management.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> {
                    if (!body.contains("jvm_memory_used_bytes")) {
                        throw new AssertionError("sin métricas JVM");
                    }
                });
    }

    @Test
    void gatewayRouteManagementEndpointIsDisabled() {
        client(managementPort).get().uri("/actuator/gateway/routes").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void publicPortDoesNotServeActuator() {
        client(publicPort).get().uri("/actuator/prometheus").exchange()
                .expectStatus().value(status -> {
                    if (status == HttpStatus.OK.value()) {
                        throw new AssertionError("/actuator expuesto en el puerto público");
                    }
                });
    }
}
