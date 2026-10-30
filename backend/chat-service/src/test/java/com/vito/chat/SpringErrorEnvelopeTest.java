package com.vito.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** T-B28: 404, 405 y 401 de Spring con el envelope de ADR-003. */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db"
})
class SpringErrorEnvelopeTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void rutaInexistenteDaNotFoundConEnvelope() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/chat/a/b/c/d/e", Map.class);

        assertEquals(404, response.getStatusCode().value());
        assertEnvelope(response.getBody(), "NOT_FOUND", 404);
    }

    @Test
    void metodoNoSoportadoDaMethodNotAllowedConEnvelope() {
        ResponseEntity<Map> response = rest.exchange("/api/v1/chat/messages", HttpMethod.DELETE, null, Map.class);

        assertEquals(405, response.getStatusCode().value());
        assertEnvelope(response.getBody(), "VALIDATION_ERROR", 405);
    }

    @Test
    void sinTokenEnRutaProtegidaDaUnauthorizedConEnvelope() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/privada", Map.class);

        assertEquals(401, response.getStatusCode().value());
        assertEnvelope(response.getBody(), "UNAUTHORIZED", 401);
    }

    private static void assertEnvelope(Map<?, ?> body, String error, int status) {
        assertNotNull(body);
        assertEquals(error, body.get("error"));
        assertEquals(status, body.get("status"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }
}
