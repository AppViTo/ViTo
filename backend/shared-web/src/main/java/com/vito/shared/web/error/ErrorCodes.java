package com.vito.shared.web.error;

/** Códigos canónicos de {@code ErrorResponse.error} (CONVENTIONS §3.3) que usan los servicios. */
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /** Una dependencia no respondió a tiempo; va con {@code Retry-After} (fixtures {@code *.503.json}). */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private ErrorCodes() {
    }
}
