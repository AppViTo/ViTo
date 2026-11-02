package com.vito.event.domain.port.outbound;

import com.vito.event.domain.model.Event;

import java.util.List;
import java.util.Optional;

public interface EventRepository {
    
    Event save(Event event);
    
    Optional<Event> findById(String eventId);
    
    List<Event> findByOrganizerId(String organizerId, int page, int size);
    
    List<Event> findByStatus(Event.EventStatus status, int page, int size);
    
    List<Event> findUpcomingEvents(int page, int size);
    
    List<Event> findByTag(String tag, int page, int size);
    
    void delete(String eventId);
    
    boolean existsById(String eventId);
    
    /**
     * Ocupa un lugar con un {@code $inc} condicional (R1): solo si el evento está
     * {@code PUBLISHED} y {@code maxAttendees == 0 || totalAccepted < maxAttendees}.
     * @return {@code false} si no hay cupo o el evento no admite confirmaciones
     */
    boolean tryReserveSeat(String eventId);
    
    /** Libera un lugar ({@code totalAccepted - 1}, nunca por debajo de 0). */
    void releaseSeat(String eventId);
    
    /** {@code $inc} sobre un contador de {@code stats}; nunca reescribe el documento ni baja de 0. */
    void incrementStat(String eventId, StatCounter counter, int delta);
    
    /** Contadores de {@code stats} que no son el cupo (el cupo va por {@link #tryReserveSeat}). */
    enum StatCounter {
        INVITED("stats.totalInvited"),
        DECLINED("stats.totalDeclined"),
        MAYBE("stats.totalMaybe"),
        WAITLISTED("stats.totalWaitlisted"),
        CHECKED_IN("stats.totalCheckedIn");
        
        private final String field;
        
        StatCounter(String field) {
            this.field = field;
        }
        
        public String field() {
            return field;
        }
    }
}
