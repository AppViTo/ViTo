package com.vito.archive.infrastructure.adapter.outbound.kafka;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.model.EventArchive;
import com.vito.archive.domain.port.outbound.ArchiveEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaArchiveEventPublisher implements ArchiveEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String TOPIC_ARCHIVE_EVENTS = "archive-events";
    private static final String TOPIC_MEDIA_EVENTS = "media-events";
    private static final String TOPIC_COMMENT_EVENTS = "comment-events";
    
    @Override
    public void publishArchiveCreated(EventArchive archive) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "ARCHIVE_CREATED");
        event.put("archiveId", archive.getId());
        event.put("eventId", archive.getEventId());
        event.put("organizerId", archive.getOrganizerId());
        event.put("title", archive.getTitle());
        
        kafkaTemplate.send(TOPIC_ARCHIVE_EVENTS, archive.getId(), event);
        log.info("Published ARCHIVE_CREATED event for archive: {}", archive.getId());
    }
    
    @Override
    public void publishArchiveUpdated(EventArchive archive) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "ARCHIVE_UPDATED");
        event.put("archiveId", archive.getId());
        event.put("eventId", archive.getEventId());
        event.put("title", archive.getTitle());
        event.put("isPublic", archive.getSharing().isIsPublic());
        
        kafkaTemplate.send(TOPIC_ARCHIVE_EVENTS, archive.getId(), event);
        log.info("Published ARCHIVE_UPDATED event for archive: {}", archive.getId());
    }
    
    @Override
    public void publishMediaUploaded(String archiveId, ArchiveMedia media) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "MEDIA_UPLOADED");
        event.put("archiveId", archiveId);
        event.put("mediaId", media.getId());
        event.put("type", media.getType().name());
        event.put("uploaderId", media.getUploaderId());
        
        kafkaTemplate.send(TOPIC_MEDIA_EVENTS, media.getId(), event);
        log.info("Published MEDIA_UPLOADED event for media: {}", media.getId());
    }
    
    @Override
    public void publishCommentAdded(String archiveId, ArchiveComment comment) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "COMMENT_ADDED");
        event.put("archiveId", archiveId);
        event.put("commentId", comment.getId());
        event.put("userId", comment.getUserId());
        event.put("mediaId", comment.getMediaId());
        
        kafkaTemplate.send(TOPIC_COMMENT_EVENTS, comment.getId(), event);
        log.info("Published COMMENT_ADDED event for comment: {}", comment.getId());
    }
    
    @Override
    public void publishMediaLiked(String archiveId, String mediaId, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "MEDIA_LIKED");
        event.put("archiveId", archiveId);
        event.put("mediaId", mediaId);
        event.put("userId", userId);
        
        kafkaTemplate.send(TOPIC_MEDIA_EVENTS, mediaId, event);
        log.info("Published MEDIA_LIKED event for media: {}", mediaId);
    }
}
