package com.vito.auth.domain.exception;

import java.util.Map;

/**
 * Datos de la petición que no cumplen una regla del contrato: {@code 400 VALIDATION_ERROR} con
 * {@code details} por campo (ADR-003). Mensajes en es-ES (CONVENTIONS §3.3).
 */
public class InvalidRequestException extends RuntimeException {

    private final Map<String, String> details;

    public InvalidRequestException(String message, Map<String, String> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }

    /** {@code searchUsers}: texto de 2 a 50 caracteres tras quitar la @ (fixture searchUsers.400). */
    public static InvalidRequestException queryOutOfRange(int min, int max) {
        return new InvalidRequestException("La búsqueda debe tener entre " + min + " y " + max + " caracteres",
                Map.of("query", "Entre " + min + " y " + max + " caracteres"));
    }

    /** {@code page} negativa o {@code size} fuera de rango (mismo texto que listFollowers.400). */
    public static InvalidRequestException invalidPagination(String field, String detail) {
        return new InvalidRequestException("La paginación no es válida", Map.of(field, detail));
    }

    /** {@code getUsersByIds}: entre 1 y 50 ids (fixture getUsersByIds.400). */
    public static InvalidRequestException idsOutOfRange(int max) {
        return new InvalidRequestException("Debes pedir entre 1 y " + max + " usuarios",
                Map.of("ids", "Entre 1 y " + max + " ids"));
    }
}
