package com.vito.event.domain.port.inbound;

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
    
    Event getEvent(String eventId);
    
    List<Event> getUserEvents(String userId, int page, int size);
    
    List<Event> getUpcomingEvents(int page, int size);
    
    RSVP respondToRSVP(RespondRSVPCommand command);
    
    List<RSVP> getEventAttendees(String eventId, int page, int size);
    
    Invitation sendInvitation(SendInvitationCommand command);
    
    List<Invitation> getEventInvitations(String eventId, int page, int size);
    
    void respondToInvitation(RespondInvitationCommand command);
    
    String generateCheckInQR(String eventId, String userId);
    
    RSVP checkIn(CheckInCommand command);
    
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
