package com.vito.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-B27: con los perfiles de docker-compose.prod.yml no se registra en DEBUG (application-prod.yml). */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db",
        "S3_ACCESS_KEY=test",
        "S3_SECRET_KEY=test"
})
@ActiveProfiles("prod")
class ProdLoggingProfileTest {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${management.tracing.sampling.probability}")
    private double tracingProbability;

    @Value("${management.otlp.tracing.endpoint}")
    private String otlpEndpoint;


    @Test
    void logsAtInfoWithoutDebug() {
        String logger = "com.vito.media";
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
