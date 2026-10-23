package com.vito.checkin.domain.exception;

/**
 * Error de dominio de checkin-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class CheckInException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;

    public CheckInException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static CheckInException unauthenticated() {
        return new CheckInException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    public static CheckInException ticketNotFound() {
        return new CheckInException(Reason.NOT_FOUND, "El ticket de check-in no existe");
    }

    public static CheckInException invalidQRCode() {
        return new CheckInException(Reason.INVALID, "El código QR no es válido");
    }

    public static CheckInException ticketExpired() {
        return new CheckInException(Reason.INVALID, "El ticket de check-in expiró");
    }

    public static CheckInException ticketAlreadyUsed() {
        return new CheckInException(Reason.CONFLICT, "El ticket de check-in ya se usó");
    }

    public static CheckInException ticketRevoked() {
        return new CheckInException(Reason.INVALID, "El ticket de check-in fue revocado");
    }

    public static CheckInException notAuthorized() {
        return new CheckInException(Reason.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }

    public static CheckInException eventNotFound() {
        return new CheckInException(Reason.NOT_FOUND, "El evento no existe");
    }

    public static CheckInException alreadyHasTicket() {
        return new CheckInException(Reason.CONFLICT, "Ya tienes un ticket de check-in para este evento");
    }
}
