package com.vito.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.vito.gateway.security.JwtAuthenticationFilterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** En {@code prod} el gateway no registra en DEBUG (application-prod.yml). */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@ActiveProfiles({"prod", "docker"})
class ProdLoggingProfileTest {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${management.tracing.sampling.probability}")
    private double tracingProbability;

    @Value("${management.otlp.tracing.endpoint}")
    private String otlpEndpoint;


    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.vito.gateway", "org.springframework.cloud.gateway", "org.springframework.security"})
    void logsAtInfoWithoutDebug(String logger) {
        assertFalse(LoggerFactory.getLogger(logger).isDebugEnabled(), logger + " sigue en DEBUG en prod");
        assertTrue(LoggerFactory.getLogger(logger).isInfoEnabled(), logger + " no registra INFO en prod");
    }

    @Test
    void emitsJsonLogsAndExportsTracesToAlloy(CapturedOutput output) {
        LoggerFactory.getLogger("com.vito.observability").info("observability probe");

        String logs = output.getAll();
        assertTrue(logs.contains("\"service\":\"" + applicationName + "\""), logs);
        assertTrue(logs.contains("\"message\":\"observability probe\""), logs);
        assertTrue(logs.contains("\"level\":\"INFO\""), logs);
        assertTrue(tracingProbability > 0.0 && tracingProbability <= 1.0, "sampling inválido");
        assertTrue(otlpEndpoint.endsWith("/v1/traces"), otlpEndpoint);
    }

}
