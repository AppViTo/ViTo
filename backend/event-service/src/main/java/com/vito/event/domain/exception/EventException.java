package com.vito.event.domain.exception;

import java.util.Map;

/**
 * Error de dominio de event-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class EventException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;
    private final Map<String, String> details;

    public EventException(Reason reason, String message) {
        this(reason, message, null);
    }

    /** {@code details}: {@code {"campo": "motivo"}} del envelope de validación (CONVENTIONS §3.3). */
    public EventException(Reason reason, String message, Map<String, String> details) {
        super(message);
        this.reason = reason;
        this.details = details == null ? null : Map.copyOf(details);
    }

    public Reason getReason() {
        return reason;
    }

    /** Detalle por campo, o {@code null} si el error no lo lleva. */
    public Map<String, String> getDetails() {
        return details;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** {@code scope} fuera del enum de listEvents (ADR-014); texto del fixture listEvents.400. */
    public static EventException invalidListScope() {
        return new EventException(Reason.INVALID, "El scope debe ser mine o upcoming",
                Map.of("scope", "Valores permitidos: mine, upcoming"));
    }

    /** {@code size} mayor que el máximo de página (ADR-008). */
    public static EventException pageSizeTooLarge(int max) {
        return new EventException(Reason.INVALID, "El tamaño de página máximo es " + max,
                Map.of("size", "Máximo " + max));
    }

    /** {@code page} negativo: la paginación es base 0 (CONVENTIONS §5). */
    public static EventException negativePage() {
        return new EventException(Reason.INVALID, "La página no puede ser negativa",
                Map.of("page", "Mínimo 0"));
    }

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static EventException unauthenticated() {
        return unauthenticated("Debes iniciar sesión");
    }

    /** Variante con el mensaje que el contrato fija para una operación (p. ej. listEvents.401). */
    public static EventException unauthenticated(String message) {
        return new EventException(Reason.UNAUTHORIZED, message);
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
