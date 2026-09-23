package com.vito.event.domain.port.outbound;

import com.vito.event.domain.model.RSVP;

import java.util.List;
import java.util.Optional;

public interface RSVPRepository {
    
    RSVP save(RSVP rsvp);
    
    Optional<RSVP> findByEventIdAndUserId(String eventId, String userId);
    
    List<RSVP> findByEventId(String eventId, int page, int size);
    
    List<RSVP> findByUserId(String userId, int page, int size);
    
    List<RSVP> findByEventIdAndStatus(String eventId, RSVP.RSVPStatus status);
    
    long countByEventId(String eventId);
    
    long countByEventIdAndStatus(String eventId, RSVP.RSVPStatus status);
    
    void delete(String eventId, String userId);
    
    boolean existsByEventIdAndUserId(String eventId, String userId);
}
