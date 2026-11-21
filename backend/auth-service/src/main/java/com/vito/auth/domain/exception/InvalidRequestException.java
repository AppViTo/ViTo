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

    /** {@code getUsersByIds}: entre 1 y 50 ids (fixture getUsersByIds.400). */
    public static InvalidRequestException idsOutOfRange(int max) {
        return new InvalidRequestException("Debes pedir entre 1 y " + max + " usuarios",
                Map.of("ids", "Entre 1 y " + max + " ids"));
    }
}
