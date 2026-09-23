package com.vito.archive.application.service;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.model.EventArchive;
import com.vito.archive.domain.port.inbound.ArchiveService;
import com.vito.archive.domain.port.outbound.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements ArchiveService {
    
    private final EventArchiveRepository archiveRepository;
    private final ArchiveMediaRepository mediaRepository;
    private final ArchiveCommentRepository commentRepository;
    private final ShareService shareService;
    private final ArchiveEventPublisher eventPublisher;
    
    @Override
    public EventArchive createArchive(CreateArchiveCommand command) {
        log.info("Creating archive for event: {}", command.getEventId());
        
        String archiveId = UUID.randomUUID().toString();
        String shareCode = shareService.generateShareCode(archiveId);
        String shareUrl = shareService.generateShareUrl(archiveId, shareCode);
        
        EventArchive archive = EventArchive.builder()
                .id(archiveId)
                .eventId(command.getEventId())
                .organizerId(command.getOrganizerId())
                .title(command.getTitle())
                .description(command.getDescription())
                .status(EventArchive.ArchiveStatus.PENDING)
                .statistics(EventArchive.EventStatistics.builder().build())
                .sharing(EventArchive.SharingSettings.builder()
                        .isPublic(false)
                        .allowComments(true)
                        .allowReactions(true)
                        .allowSharing(true)
                        .shareUrl(shareUrl)
                        .shareCode(shareCode)
                        .build())
                .createdAt(LocalDateTime.now())
                .build();
        
        EventArchive savedArchive = archiveRepository.save(archive);
        eventPublisher.publishArchiveCreated(savedArchive);
        
        return savedArchive;
    }
    
    @Override
    public EventArchive getArchive(String archiveId) {
        return archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RuntimeException("Archive not found: " + archiveId));
    }
    
    @Override
    public EventArchive getArchiveByEventId(String eventId) {
        return archiveRepository.findByEventId(eventId)
                .orElseThrow(() -> new RuntimeException("Archive not found for event: " + eventId));
    }
    
    @Override
    public List<EventArchive> getPublicArchives(int page, int size) {
        return archiveRepository.findByIsPublicTrue(page, size);
    }
    
    @Override
    public List<EventArchive> getUserArchives(String userId, int page, int size) {
        return archiveRepository.findByOrganizerId(userId, page, size);
    }
    
    @Override
    public EventArchive updateArchive(String archiveId, UpdateArchiveCommand command) {
        EventArchive archive = getArchive(archiveId);
        
        if (command.getTitle() != null) {
            archive.setTitle(command.getTitle());
        }
        if (command.getDescription() != null) {
            archive.setDescription(command.getDescription());
        }
        if (command.getCoverUrl() != null) {
            archive.setCoverUrl(command.getCoverUrl());
        }
        if (command.getIsPublic() != null) {
            archive.getSharing().setIsPublic(command.getIsPublic());
        }
        if (command.getAllowComments() != null) {
            archive.getSharing().setAllowComments(command.getAllowComments());
        }
        if (command.getAllowReactions() != null) {
            archive.getSharing().setAllowReactions(command.getAllowReactions());
        }
        if (command.getAllowSharing() != null) {
            archive.getSharing().setAllowSharing(command.getAllowSharing());
        }
        
        EventArchive updatedArchive = archiveRepository.save(archive);
        eventPublisher.publishArchiveUpdated(updatedArchive);
        
        return updatedArchive;
    }
    
    @Override
    public ArchiveMedia uploadMedia(String archiveId, UploadMediaCommand command) {
        log.info("Uploading media to archive: {}", archiveId);
        
        ArchiveMedia media = ArchiveMedia.builder()
                .id(UUID.randomUUID().toString())
                .archiveId(archiveId)
                .eventId(getArchive(archiveId).getEventId())
                .uploaderId(command.getUploaderId())
                .type(command.getType())
                .url(command.getUrl())
                .thumbnailUrl(command.getThumbnailUrl())
                .caption(command.getCaption())
                .metadata(command.getMetadata())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        ArchiveMedia savedMedia = mediaRepository.save(media);
        
        // Update archive statistics
        EventArchive archive = getArchive(archiveId);
        archive.getStatistics().setTotalPhotos(
                archive.getStatistics().getTotalPhotos() + 1
        );
        archiveRepository.save(archive);
        
        eventPublisher.publishMediaUploaded(archiveId, savedMedia);
        
        return savedMedia;
    }
    
    @Override
    public List<ArchiveMedia> getMedia(String archiveId, int page, int size) {
        return mediaRepository.findByArchiveId(archiveId, page, size);
    }
    
    @Override
    public ArchiveMedia getMediaById(String mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new RuntimeException("Media not found: " + mediaId));
    }
    
    @Override
    public ArchiveComment addComment(String archiveId, AddCommentCommand command) {
        log.info("Adding comment to archive: {}", archiveId);
        
        ArchiveComment comment = ArchiveComment.builder()
                .id(UUID.randomUUID().toString())
                .archiveId(archiveId)
                .mediaId(command.getMediaId())
                .userId(command.getUserId())
                .username(command.getUsername())
                .displayName(command.getDisplayName())
                .avatar(command.getAvatar())
                .content(command.getContent())
                .parentId(command.getParentId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        ArchiveComment savedComment = commentRepository.save(comment);
        
        // Update archive statistics
        EventArchive archive = getArchive(archiveId);
        archive.getStatistics().setTotalComments(
                archive.getStatistics().getTotalComments() + 1
        );
        archiveRepository.save(archive);
        
        eventPublisher.publishCommentAdded(archiveId, savedComment);
        
        return savedComment;
    }
    
    @Override
    public List<ArchiveComment> getComments(String archiveId, String mediaId, int page, int size) {
        if (mediaId != null) {
            return commentRepository.findByMediaId(mediaId, page, size);
        }
        return commentRepository.findByArchiveId(archiveId, page, size);
    }
    
    @Override
    public ArchiveComment updateComment(String commentId, String userId, String content) {
        ArchiveComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found: " + commentId));
        
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this comment");
        }
        
        comment.setContent(content);
        comment.setUpdatedAt(LocalDateTime.now());
        
        return commentRepository.save(comment);
    }
    
    @Override
    public void deleteComment(String commentId, String userId) {
        ArchiveComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found: " + commentId));
        
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this comment");
        }
        
        commentRepository.deleteById(commentId);
        
        // Update archive statistics
        EventArchive archive = getArchive(comment.getArchiveId());
        archive.getStatistics().setTotalComments(
                Math.max(0, archive.getStatistics().getTotalComments() - 1)
        );
        archiveRepository.save(archive);
    }
    
    @Override
    public void likeMedia(String mediaId, String userId) {
        ArchiveMedia media = getMediaById(mediaId);
        media.setLikesCount(media.getLikesCount() + 1);
        mediaRepository.save(media);
        
        // Update archive statistics
        EventArchive archive = getArchive(media.getArchiveId());
        archive.getStatistics().setTotalReactions(
                archive.getStatistics().getTotalReactions() + 1
        );
        archiveRepository.save(archive);
        
        eventPublisher.publishMediaLiked(media.getArchiveId(), mediaId, userId);
    }
    
    @Override
    public void unlikeMedia(String mediaId, String userId) {
        ArchiveMedia media = getMediaById(mediaId);
        media.setLikesCount(Math.max(0, media.getLikesCount() - 1));
        mediaRepository.save(media);
        
        // Update archive statistics
        EventArchive archive = getArchive(media.getArchiveId());
        archive.getStatistics().setTotalReactions(
                Math.max(0, archive.getStatistics().getTotalReactions() - 1)
        );
        archiveRepository.save(archive);
    }
    
    @Override
    public void archiveEvent(String eventId) {
        log.info("Archiving event: {}", eventId);
        
        EventArchive archive = getArchiveByEventId(eventId);
        archive.setStatus(EventArchive.ArchiveStatus.PROCESSING);
        archiveRepository.save(archive);
        
        // TODO: Trigger async processing (video encoding, thumbnail generation, etc.)
        // For now, mark as completed
        archive.setStatus(EventArchive.ArchiveStatus.COMPLETED);
        archive.setArchivedAt(LocalDateTime.now());
        archiveRepository.save(archive);
    }
}
