package com.vito.event.domain.port.outbound;

import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRepository {
    
    Event save(Event event);
    
    Optional<Event> findById(String eventId);
    
    List<Event> findByOrganizerId(String organizerId, int page, int size);
    
    List<Event> findByStatus(Event.EventStatus status, int page, int size);
    
    List<Event> findUpcomingEvents(int page, int size);

    /** R10: de {@code ids}, los {@code PUBLISHED} con {@code endDate} posterior a {@code now}, por {@code startDate} ascendente. */
    List<Event> findActiveByIds(Collection<String> ids, Instant now, int page, int size);
    
    List<Event> findByTag(String tag, int page, int size);
    
    void delete(String eventId);
    
    boolean existsById(String eventId);
    
    /**
     * Ocupa un lugar con un {@code $inc} condicional (R1): solo si el evento está
     * {@code PUBLISHED} y {@code maxAttendees == 0 || totalAccepted < maxAttendees}.
     * @return {@code false} si no hay cupo o el evento no admite confirmaciones
     */
    boolean tryReserveSeat(String eventId);
    
    /**
     * US2: aplica solo los campos del parche con {@code $set}/{@code $unset}, sin reescribir
     * {@code stats} (que el RSVP cambia con {@code $inc}). Es condicional en una sola operación:
     * el evento sigue editable en {@code now} y, si el parche fija un cupo distinto de 0,
     * {@code totalAccepted <= maxAttendees} (FR-021).
     * @return el evento actualizado, o vacío si alguna condición dejó de cumplirse
     */
    Optional<Event> applyPatch(String eventId, EventPatch patch, String organizerDisplayName, Instant now);

    /**
     * US5: pasa el evento a {@code CANCELLED} con un único {@code $set} condicional (no
     * cancelado, no completado y sin terminar en {@code now}). No toca {@code stats},
     * invitaciones, RSVP ni enlaces (FR-022).
     * @return {@code true} si lo canceló esta llamada
     */
    boolean cancel(String eventId, Instant now);

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
