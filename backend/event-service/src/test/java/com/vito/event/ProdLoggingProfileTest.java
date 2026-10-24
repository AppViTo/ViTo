package com.vito.event;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-B27: con los perfiles de docker-compose.prod.yml no se registra en DEBUG (application-prod.yml). */
@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db",
        "QR_SECRET=test-qr-secret-for-unit-tests-only"
})
@ActiveProfiles("prod")
class ProdLoggingProfileTest {

    @ParameterizedTest
    @ValueSource(strings = {"com.vito", "org.springframework.security"})
    void logsAtInfoWithoutDebug(String logger) {
        assertFalse(LoggerFactory.getLogger(logger).isDebugEnabled(), logger + " sigue en DEBUG en prod");
        assertTrue(LoggerFactory.getLogger(logger).isInfoEnabled(), logger + " no registra INFO en prod");
    }
}
