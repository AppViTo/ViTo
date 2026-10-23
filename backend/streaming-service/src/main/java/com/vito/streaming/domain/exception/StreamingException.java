package com.vito.streaming.domain.exception;

/**
 * Error de dominio de streaming-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class StreamingException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;

    public StreamingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static StreamingException unauthenticated() {
        return new StreamingException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    public static StreamingException streamNotFound() {
        return new StreamingException(Reason.NOT_FOUND, "La transmisión no existe");
    }

    public static StreamingException notAuthorized() {
        return new StreamingException(Reason.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }

    public static StreamingException streamAlreadyLive() {
        return new StreamingException(Reason.CONFLICT, "La transmisión ya está en vivo");
    }

    public static StreamingException streamNotLive() {
        return new StreamingException(Reason.CONFLICT, "La transmisión no está en vivo");
    }

    public static StreamingException invalidStreamKey() {
        return new StreamingException(Reason.INVALID, "La clave de transmisión no es válida");
    }

    public static StreamingException maxViewersReached() {
        return new StreamingException(Reason.CONFLICT, "La transmisión alcanzó el máximo de espectadores");
    }

    public static StreamingException chatDisabled() {
        return new StreamingException(Reason.FORBIDDEN, "El chat está desactivado en esta transmisión");
    }

    public static StreamingException reactionsDisabled() {
        return new StreamingException(Reason.FORBIDDEN, "Las reacciones están desactivadas en esta transmisión");
    }
}
