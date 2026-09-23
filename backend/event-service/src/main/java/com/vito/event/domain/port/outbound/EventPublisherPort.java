package com.vito.event.domain.port.outbound;

public interface EventPublisherPort {
    
    void publishEventCreated(String eventId, String organizerId);
    
    void publishEventUpdated(String eventId, String organizerId);
    
    void publishEventCancelled(String eventId, String organizerId);
    
    void publishRSVPChanged(String eventId, String userId, String status);
    
    void publishInvitationSent(String eventId, String inviterId, String inviteeId);
    
    void publishCheckIn(String eventId, String userId);
}
