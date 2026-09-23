package com.vito.archive.domain.port.outbound;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.model.EventArchive;

public interface ArchiveEventPublisher {
    
    void publishArchiveCreated(EventArchive archive);
    
    void publishArchiveUpdated(EventArchive archive);
    
    void publishMediaUploaded(String archiveId, ArchiveMedia media);
    
    void publishCommentAdded(String archiveId, ArchiveComment comment);
    
    void publishMediaLiked(String archiveId, String mediaId, String userId);
}
