package com.vito.event.domain.exception;

/**
 * Error de dominio de event-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class EventException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;

    public EventException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static EventException unauthenticated() {
        return new EventException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    public static EventException eventNotFound() {
        return new EventException(Reason.NOT_FOUND, "El evento no existe");
    }

    public static EventException notAuthorized() {
        return new EventException(Reason.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }

    public static EventException eventAlreadyCancelled() {
        return new EventException(Reason.CONFLICT, "El evento ya está cancelado");
    }

    public static EventException eventIsFull() {
        return new EventException(Reason.CONFLICT, "El evento alcanzó su capacidad máxima");
    }

    public static EventException alreadyRSVP() {
        return new EventException(Reason.CONFLICT, "Ya respondiste a este evento");
    }

    public static EventException cannotRSVPToOwnEvent() {
        return new EventException(Reason.INVALID, "No puedes responder a tu propio evento");
    }

    public static EventException invitationNotFound() {
        return new EventException(Reason.NOT_FOUND, "La invitación no existe");
    }

    public static EventException invitationExpired() {
        return new EventException(Reason.INVALID, "La invitación expiró");
    }

    public static EventException alreadyInvited() {
        return new EventException(Reason.CONFLICT, "Este usuario ya está invitado al evento");
    }

    public static EventException invalidCheckIn() {
        return new EventException(Reason.INVALID, "El código de check-in no es válido");
    }

    public static EventException alreadyCheckedIn() {
        return new EventException(Reason.CONFLICT, "Ya hiciste check-in en este evento");
    }
}
