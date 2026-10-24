package com.vito.gateway.config;

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
@SpringBootTest
@ActiveProfiles({"prod", "docker"})
class ProdLoggingProfileTest {

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
}
