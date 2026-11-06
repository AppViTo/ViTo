package com.vito.event.domain.port.outbound;

import com.vito.event.domain.model.RSVP;

import java.time.Instant;
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

    /** R10: eventos en los que la persona respondió algo distinto de {@code DECLINED}. */
    List<String> findEventIdsAnsweredBy(String userId);
    
    /**
     * Pasa a {@code ACCEPTED} el {@code WAITLISTED} más antiguo del evento (por {@code waitlistedAt})
     * en una sola operación. Devuelve el RSVP tal como estaba antes, para poder revertirlo si
     * después no se consigue el lugar (R1).
     */
    Optional<RSVP> promoteOldestWaitlisted(String eventId);
    
    /** Deshace una promoción sin lugar: vuelve a {@code WAITLISTED} solo si sigue {@code ACCEPTED}. */
    void revertToWaitlisted(String rsvpId, Instant waitlistedAt);
}
