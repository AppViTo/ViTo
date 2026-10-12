package com.vito.archive.domain.port.inbound;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.model.EventArchive;

import java.util.List;

public interface ArchiveService {
    
    EventArchive createArchive(CreateArchiveCommand command);
    
    EventArchive getArchive(String archiveId);
    
    EventArchive getArchiveByEventId(String eventId);
    
    List<EventArchive> getPublicArchives(int page, int size);
    
    List<EventArchive> getUserArchives(String userId, int page, int size);
    
    EventArchive updateArchive(String archiveId, UpdateArchiveCommand command);
    
    ArchiveMedia uploadMedia(String archiveId, UploadMediaCommand command);
    
    List<ArchiveMedia> getMedia(String archiveId, int page, int size);
    
    ArchiveMedia getMediaById(String mediaId);
    
    ArchiveComment addComment(String archiveId, AddCommentCommand command);
    
    List<ArchiveComment> getComments(String archiveId, String mediaId, int page, int size);
    
    ArchiveComment updateComment(String commentId, String userId, String content);
    
    void deleteComment(String commentId, String userId);
    
    void likeMedia(String mediaId, String userId);
    
    void unlikeMedia(String mediaId, String userId);
    
    void archiveEvent(String eventId);
    
    @lombok.Data
    class CreateArchiveCommand {
        private String eventId;
        private String organizerId;
        private String title;
        private String description;
    }
    
    @lombok.Data
    class UpdateArchiveCommand {
        private String title;
        private String description;
        private String coverUrl;
        private Boolean isPublic;
        private Boolean allowComments;
        private Boolean allowReactions;
        private Boolean allowSharing;
    }
    
    @lombok.Data
    class UploadMediaCommand {
        private String uploaderId;
        private ArchiveMedia.MediaType type;
        private String url;
        private String thumbnailUrl;
        private String caption;
        private ArchiveMedia.MediaMetadata metadata;
    }
    
    @lombok.Data
    class AddCommentCommand {
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
        private String content;
        private String mediaId;
        private String parentId;
    }
}
