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
        return new EventException(Reason.INVALID, "El scope debe ser mine, upcoming o invited",
                Map.of("scope", "Valores permitidos: mine, upcoming, invited"));
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

    /** RSVP con un {@code status} que no es de entrada; texto del fixture respondRsvp.400. */
    public static EventException invalidRsvpStatus() {
        return new EventException(Reason.INVALID, "El status debe ser ACCEPTED, DECLINED o MAYBE",
                Map.of("status", "Valores permitidos: ACCEPTED, DECLINED, MAYBE"));
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

    /** respondRsvp.409 */
    public static EventException cannotRSVPToOwnEvent() {
        return new EventException(Reason.CONFLICT, "No puedes responder RSVP a tu propio evento");
    }

    // ---- Enlaces de invitación (US1); textos de los fixtures *InviteLink*

    /** FR-006: inexistente, vencido o revocado responden igual, sin revelar cuál es el caso. */
    public static EventException inviteLinkUnavailable() {
        return new EventException(Reason.NOT_FOUND, "El enlace no existe o ya no está disponible");
    }

    public static EventException inviteLinkExhausted() {
        return new EventException(Reason.CONFLICT, "El enlace alcanzó su límite de usos");
    }

    /** FR-007: evento cancelado, terminado o sin publicar. */
    public static EventException eventNotAcceptingGuests() {
        return new EventException(Reason.CONFLICT, "El evento ya no admite invitados");
    }

    public static EventException eventOrLinkNotFound() {
        return new EventException(Reason.NOT_FOUND, "El evento o el enlace no existe");
    }

    public static EventException onlyOrganizer(String message) {
        return new EventException(Reason.FORBIDDEN, message);
    }

    public static EventException invalidExpiresInDays(int min, int max) {
        return new EventException(Reason.INVALID, "expiresInDays debe estar entre " + min + " y " + max,
                Map.of("expiresInDays", "Rango permitido: " + min + "–" + max));
    }

    public static EventException invalidMaxUses(int min, int max) {
        return new EventException(Reason.INVALID, "maxUses debe estar entre " + min + " y " + max,
                Map.of("maxUses", "Rango permitido: " + min + "–" + max));
    }

    /** Texto de listInviteLinks.400 (distinto del de listEvents). */
    public static EventException linkPageSizeTooLarge(int max) {
        return new EventException(Reason.INVALID, "El tamaño de página no puede superar " + max,
                Map.of("size", "Máximo " + max));
    }

    /** R3: evento terminado, con {@code endDate} pasada o sin publicar. */
    // ---- updateEvent (US2) -----------------------------------------------------------

    /** Campo del recurso que no se edita; texto del fixture updateEvent.400. */
    public static EventException notEditableField(String field) {
        return new EventException(Reason.INVALID, "No se puede editar el campo " + field,
                Map.of(field, "Campo no editable"));
    }

    /** {@code additionalProperties: false} de UpdateEventRequest. */
    public static EventException unknownField(String field) {
        return new EventException(Reason.INVALID, "El campo " + field + " no existe",
                Map.of(field, "Campo desconocido"));
    }

    /** Valor de un campo que no cumple el contrato; mismo texto que la validación de createEvent. */
    public static EventException invalidField(String field, String reason) {
        return new EventException(Reason.INVALID, "El campo " + field + " no es válido", Map.of(field, reason));
    }

    /** {@code minProperties: 1}. */
    public static EventException emptyPatch() {
        return new EventException(Reason.INVALID, "Envía al menos un campo para editar");
    }

    /** El cuerpo no es un objeto JSON. */
    public static EventException invalidPatchBody() {
        return new EventException(Reason.INVALID, "El cuerpo de la petición no es un JSON válido");
    }

    /** Texto del fixture updateEvent.403. */
    public static EventException onlyOrganizerCanEdit() {
        return new EventException(Reason.FORBIDDEN, "Solo el organizador puede editar este evento");
    }

    /** Evento cancelado, completado o con {@code endDate} pasada (fixture updateEvent.state.409). */
    public static EventException eventNotEditable() {
        return new EventException(Reason.CONFLICT, "No se puede editar un evento cancelado o terminado");
    }

    /** FR-021 (fixture updateEvent.capacity.409). */
    public static EventException capacityBelowAccepted(int accepted) {
        return new EventException(Reason.CONFLICT, "El cupo no puede ser menor que los " + accepted + " confirmados");
    }

    // ---- cancelEvent (US5) -----------------------------------------------------------

    /** Texto del fixture cancelEvent.403. */
    public static EventException onlyOrganizerCanCancel() {
        return new EventException(Reason.FORBIDDEN, "Solo el organizador puede cancelar este evento");
    }

    /** {@code COMPLETED} o con {@code endDate} pasada (fixture cancelEvent.409). */
    public static EventException eventAlreadyEnded() {
        return new EventException(Reason.CONFLICT, "No se puede cancelar un evento que ya terminó");
    }

    public static EventException rsvpClosed() {
        return new EventException(Reason.CONFLICT, "El evento ya no admite respuestas");
    }

    public static EventException rsvpMessageTooLong(int max) {
        return new EventException(Reason.INVALID, "El mensaje admite máximo " + max + " caracteres",
                Map.of("message", "Máximo " + max + " caracteres"));
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
