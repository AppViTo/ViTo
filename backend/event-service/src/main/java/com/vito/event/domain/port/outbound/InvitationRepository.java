package com.vito.event.domain.port.outbound;

import com.vito.event.domain.model.Invitation;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository {
    
    Invitation save(Invitation invitation);
    
    Optional<Invitation> findByEventIdAndInviteeId(String eventId, String inviteeId);
    
    List<Invitation> findByEventId(String eventId, int page, int size);
    
    List<Invitation> findByInviterId(String inviterId, int page, int size);
    
    List<Invitation> findByInviteeId(String inviteeId, int page, int size);
    
    List<Invitation> findByEventIdAndStatus(String eventId, Invitation.InvitationStatus status);
    
    long countByEventId(String eventId);
    
    void delete(String eventId, String inviteeId);
    
    boolean existsByEventIdAndInviteeId(String eventId, String inviteeId);
}
