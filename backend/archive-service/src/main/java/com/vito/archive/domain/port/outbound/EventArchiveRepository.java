package com.vito.archive.domain.port.outbound;

import com.vito.archive.domain.model.EventArchive;

import java.util.List;
import java.util.Optional;

public interface EventArchiveRepository {
    
    EventArchive save(EventArchive archive);
    
    Optional<EventArchive> findById(String id);
    
    Optional<EventArchive> findByEventId(String eventId);
    
    List<EventArchive> findAll(int page, int size);
    
    List<EventArchive> findByOrganizerId(String organizerId, int page, int size);
    
    List<EventArchive> findByIsPublicTrue(int page, int size);
    
    void deleteById(String id);
}
