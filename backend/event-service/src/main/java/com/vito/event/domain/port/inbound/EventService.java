package com.vito.event.domain.port.inbound;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface EventService {
    
    Event createEvent(CreateEventCommand command);
    
    Event updateEvent(UpdateEventCommand command);
    
    void cancelEvent(String eventId, String userId);
    
    /** Aplica {@code hideAddressUntilAccepted} según quién consulta ({@code viewerId}). */
    Event getEvent(String eventId, String viewerId);
    
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
    
    List<RSVP> getEventAttendees(String eventId, int page, int size);
    
    Invitation sendInvitation(SendInvitationCommand command);
    
    List<Invitation> getEventInvitations(String eventId, int page, int size);
    
    void respondToInvitation(RespondInvitationCommand command);
    
    String generateCheckInQR(String eventId, String userId);
    
    RSVP checkIn(CheckInCommand command);

    /** Valores de {@code scope} en listEvents: enum cerrado (ADR-014). */
    enum ListScope {
        MINE("mine"),
        UPCOMING("upcoming");

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
        private String title;
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
        private String title;
        private String description;
        private Event.EventDetails details;
        private Event.EventSettings settings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RespondRSVPCommand {
        private String eventId;
        private String userId;
        private RSVP.RSVPStatus status;
        private String message;
        private int guestsCount;
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
    class RespondInvitationCommand {
        private String eventId;
        private String inviteeId;
        private Invitation.InvitationStatus status;
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
