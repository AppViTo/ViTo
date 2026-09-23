package com.vito.checkin.domain.exception;

public class CheckInException extends RuntimeException {
    
    public CheckInException(String message) {
        super(message);
    }
    
    public static CheckInException ticketNotFound() {
        return new CheckInException("Check-in ticket not found");
    }
    
    public static CheckInException invalidQRCode() {
        return new CheckInException("Invalid QR code");
    }
    
    public static CheckInException ticketExpired() {
        return new CheckInException("Check-in ticket has expired");
    }
    
    public static CheckInException ticketAlreadyUsed() {
        return new CheckInException("Check-in ticket has already been used");
    }
    
    public static CheckInException ticketRevoked() {
        return new CheckInException("Check-in ticket has been revoked");
    }
    
    public static CheckInException notAuthorized() {
        return new CheckInException("You are not authorized to perform this action");
    }
    
    public static CheckInException eventNotFound() {
        return new CheckInException("Event not found");
    }
    
    public static CheckInException alreadyHasTicket() {
        return new CheckInException("You already have a check-in ticket for this event");
    }
}
