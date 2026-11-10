package com.vito.event.domain.port.inbound;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventAccess;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.NoArgsConstructor;

import java.util.List;

public interface EventService {
    
    Event createEvent(CreateEventCommand command);
    
    Event updateEvent(UpdateEventCommand command);
    
    void cancelEvent(String eventId, String userId);
    
    /** Aplica {@code hideAddressUntilAccepted} según quién consulta ({@code viewerId}). */
    Event getEvent(String eventId, String viewerId);

    /** T-B33 (ruta INTERNO): organizador del evento y RSVP de {@code userId}; 404 si no existe. */
    EventAccess getAccess(String eventId, String userId);
    
    /**
     * listEvents (ADR-014): {@code MINE} son los eventos que organiza {@code viewerId};
     * {@code UPCOMING}, los públicos publicados y futuros. Aplica {@code hideAddressUntilAccepted}
     * a cada evento y responde {@code INVALID} si {@code size} pasa de 100 o {@code page} es negativo.
     */
    List<Event> listEvents(ListScope scope, String viewerId, int page, int size);

    List<Event> getUserEvents(String userId, int page, int size);

    /** Aplica {@code hideAddressUntilAccepted} a cada evento según quién consulta ({@code viewerId}). */
    List<Event> getUpcomingEvents(int page, int size, String viewerId);
    
    RSVP respondToRSVP(RespondRSVPCommand command);
    
    /** US6: solo el organizador; {@code status} null no filtra. */
    List<RSVP> getEventAttendees(String eventId, String userId, RSVP.RSVPStatus status, int page, int size);
    
    Invitation sendInvitation(SendInvitationCommand command);
    
    /** US4: solo el organizador; las {@code PENDING} vencidas salen {@code EXPIRED}. */
    List<Invitation> getEventInvitations(String eventId, String userId, int page, int size);
    
    String generateCheckInQR(String eventId, String userId);
    
    RSVP checkIn(CheckInCommand command);

    /** Valores de {@code scope} en listEvents: enum cerrado (ADR-014). */
    enum ListScope {
        MINE("mine"),
        UPCOMING("upcoming"),
        /** R10: invitaciones vigentes o aceptadas y RSVP distintos de DECLINED de quien consulta. */
        INVITED("invited");

        private final String value;

        ListScope(String value) {
            this.value = value;
        }

        /** Valor exacto del contrato; cualquier otro es {@code 400 VALIDATION_ERROR}. */
        public static ListScope fromValue(String value) {
            for (ListScope scope : values()) {
                if (scope.value.equals(value)) {
                    return scope;
                }
            }
            throw EventException.invalidListScope();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CreateEventCommand {
        private String organizerId;
        /** Lo pone el controlador desde la identidad (R6); se ignora lo que mande el cliente. */
        private String organizerDisplayName;
        @NotBlank(message = "Es obligatorio")
        @Size(min = 3, max = 100, message = "Mínimo 3 caracteres")
        private String title;
        @Size(max = 2000, message = "Máximo 2000 caracteres")
        private String description;
        private Event.EventDetails details;
        private Event.EventSettings settings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class UpdateEventCommand {
        private String eventId;
        private String userId;
        /** Lo pone el controlador desde la identidad (R6); se ignora lo que mande el cliente. */
        private String organizerDisplayName;
        /** Solo los campos enviados (JSON Merge Patch, US2). */
        private EventPatch patch;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RespondRSVPCommand {
        private String eventId;
        private String userId;
        /** Lo pone el controlador desde la identidad (R6); se ignora lo que mande el cliente. */
        private String displayName;
        private RSVP.RSVPStatus status;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SendInvitationCommand {
        private String eventId;
        private String inviterId;
        private String inviteeId;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CheckInCommand {
        private String eventId;
        private String userId;
        private String qrCode;
    }
}
