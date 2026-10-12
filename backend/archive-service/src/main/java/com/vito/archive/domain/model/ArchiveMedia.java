package com.vito.archive.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveMedia {
    private String id;
    private String archiveId;
    private String eventId;
    private String uploaderId;
    private MediaType type;
    private String url;
    private String thumbnailUrl;
    private String caption;
    private MediaMetadata metadata;
    private List<String> tags;
    private int likesCount;
    private int commentsCount;
    private LocalDateTime uploadedAt;
    
    public enum MediaType {
        PHOTO,
        VIDEO,
        STREAM_RECORDING,
        LIVE_SCREENSHOT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaMetadata {
        private Long fileSize;
        private Integer width;
        private Integer height;
        private Integer duration;
        private String mimeType;
        private String location;
        private LocalDateTime takenAt;
    }
}
