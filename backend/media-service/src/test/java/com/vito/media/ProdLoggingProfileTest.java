package com.vito.media;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-B27: con los perfiles de docker-compose.prod.yml no se registra en DEBUG (application-prod.yml). */
@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db",
        "S3_ACCESS_KEY=test",
        "S3_SECRET_KEY=test"
})
@ActiveProfiles("prod")
class ProdLoggingProfileTest {

    @Test
    void logsAtInfoWithoutDebug() {
        String logger = "com.vito.media";
        assertFalse(LoggerFactory.getLogger(logger).isDebugEnabled(), logger + " sigue en DEBUG en prod");
        assertTrue(LoggerFactory.getLogger(logger).isInfoEnabled(), logger + " no registra INFO en prod");
    }
}
