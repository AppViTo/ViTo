package com.vito.mosaic.domain.exception;

/**
 * Error de dominio de mosaic-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class MosaicException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;

    public MosaicException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static MosaicException unauthenticated() {
        return new MosaicException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    public static MosaicException eventNotFound() {
        return new MosaicException(Reason.NOT_FOUND, "El evento no existe");
    }

    public static MosaicException itemNotFound() {
        return new MosaicException(Reason.NOT_FOUND, "El elemento del mosaico no existe");
    }

    public static MosaicException notAuthorized() {
        return new MosaicException(Reason.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }

    /** removeMosaicItem: solo el organizador del evento modera el mosaico (excepción A3). */
    public static MosaicException notOrganizer() {
        return new MosaicException(Reason.FORBIDDEN, "Solo el organizador puede retirar fotos del mosaico");
    }

    public static MosaicException gridNotInitialized() {
        return new MosaicException(Reason.CONFLICT, "El mosaico de este evento no está inicializado");
    }

    public static MosaicException gridFull() {
        return new MosaicException(Reason.CONFLICT, "El mosaico está completo");
    }
}
