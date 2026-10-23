package com.vito.archive.domain.exception;

/**
 * Error de dominio de archive-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class ArchiveException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;

    public ArchiveException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static ArchiveException unauthenticated() {
        return new ArchiveException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    public static ArchiveException archiveNotFound() {
        return new ArchiveException(Reason.NOT_FOUND, "El archivo del evento no existe");
    }

    public static ArchiveException mediaNotFound() {
        return new ArchiveException(Reason.NOT_FOUND, "El contenido no existe");
    }

    public static ArchiveException commentNotFound() {
        return new ArchiveException(Reason.NOT_FOUND, "El comentario no existe");
    }

    public static ArchiveException notAuthorized() {
        return new ArchiveException(Reason.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }
}
