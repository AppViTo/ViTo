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
}
