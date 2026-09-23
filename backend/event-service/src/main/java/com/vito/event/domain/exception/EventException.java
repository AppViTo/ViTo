package com.vito.event.domain.exception;

public class EventException extends RuntimeException {
    
    public EventException(String message) {
        super(message);
    }
    
    public static EventException eventNotFound() {
        return new EventException("Event not found");
    }
    
    public static EventException notAuthorized() {
        return new EventException("You are not authorized to perform this action");
    }
    
    public static EventException eventAlreadyCancelled() {
        return new EventException("Event is already cancelled");
    }
    
    public static EventException eventIsFull() {
        return new EventException("Event has reached maximum capacity");
    }
    
    public static EventException alreadyRSVP() {
        return new EventException("You have already responded to this event");
    }
    
    public static EventException cannotRSVPToOwnEvent() {
        return new EventException("Cannot RSVP to your own event");
    }
    
    public static EventException invitationNotFound() {
        return new EventException("Invitation not found");
    }
    
    public static EventException invitationExpired() {
        return new EventException("Invitation has expired");
    }
    
    public static EventException alreadyInvited() {
        return new EventException("User is already invited to this event");
    }
    
    public static EventException invalidCheckIn() {
        return new EventException("Invalid check-in code");
    }
    
    public static EventException alreadyCheckedIn() {
        return new EventException("You have already checked in to this event");
    }
}
