package com.vito.archive.domain.port.outbound;

import com.vito.archive.domain.model.ArchiveComment;

import java.util.List;
import java.util.Optional;

public interface ArchiveCommentRepository {
    
    ArchiveComment save(ArchiveComment comment);
    
    Optional<ArchiveComment> findById(String id);
    
    List<ArchiveComment> findByMediaId(String mediaId, int page, int size);
    
    List<ArchiveComment> findByArchiveId(String archiveId, int page, int size);
    
    long countByArchiveId(String archiveId);
    
    void deleteById(String id);
    
    void deleteByArchiveId(String archiveId);
}
