package com.vito.archive.domain.port.outbound;

import com.vito.archive.domain.model.ArchiveMedia;

import java.util.List;
import java.util.Optional;

public interface ArchiveMediaRepository {
    
    ArchiveMedia save(ArchiveMedia media);
    
    Optional<ArchiveMedia> findById(String id);
    
    List<ArchiveMedia> findByArchiveId(String archiveId, int page, int size);
    
    List<ArchiveMedia> findByEventId(String eventId);
    
    long countByArchiveId(String archiveId);
    
    void deleteById(String id);
    
    void deleteByArchiveId(String archiveId);
}
