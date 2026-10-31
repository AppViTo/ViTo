package com.vito.gateway.error;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compara un cuerpo de error con el schema {@code ErrorResponse} de docs/api/openapi.yaml
 * (ADR-003, CONVENTIONS §3.3): si el contrato cambia, estos tests lo notan.
 */
public final class ErrorResponseContract {

    /** CONVENTIONS §3.3. */
    private static final Set<String> CANONICAL_CODES = Set.of("VALIDATION_ERROR", "UNAUTHORIZED",
            "FORBIDDEN", "NOT_FOUND", "CONFLICT", "RATE_LIMITED", "INTERNAL_ERROR", "SERVICE_UNAVAILABLE");

    private static final Path OPENAPI = Path.of("..", "..", "docs", "api", "openapi.yaml");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorResponseContract() {
    }

    public static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException e) {
            throw new AssertionError("el cuerpo no es JSON: " + json, e);
        }
    }

    public static void assertMatches(String json, int status, String error) {
        Map<String, Object> body = parse(json);
        Map<String, Object> schema = errorResponseSchema();

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        @SuppressWarnings("unchecked")
        Set<String> properties = ((Map<String, Object>) schema.get("properties")).keySet();

        assertTrue(body.keySet().containsAll(required), "faltan campos obligatorios: " + body);
        assertTrue(properties.containsAll(body.keySet()), "campos fuera del schema: " + body);

        assertEquals(error, body.get("error"));
        assertTrue(CANONICAL_CODES.contains(error), "código no canónico: " + error);
        assertEquals(status, body.get("status"));
        assertFalse(((String) body.get("message")).isBlank());

        String timestamp = (String) body.get("timestamp");
        assertTrue(timestamp.endsWith("Z"), "timestamp sin Z: " + timestamp);
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorResponseSchema() {
        assertTrue(Files.exists(OPENAPI), "no se encuentra " + OPENAPI.toAbsolutePath());
        try (InputStream in = Files.newInputStream(OPENAPI)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            return (Map<String, Object>) schemas.get("ErrorResponse");
        } catch (IOException e) {
            throw new AssertionError("no se pudo leer " + OPENAPI, e);
        }
    }
}
