package com.vito.media.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "media")
@CompoundIndexes({
    @CompoundIndex(name = "event_type_idx", def = "{'eventId': 1, 'type': 1}"),
    @CompoundIndex(name = "user_event_idx", def = "{'uploadedBy': 1, 'eventId': 1}")
})
public class Media {

    @Id
    private String id;

    @Indexed
    private String eventId;

    @Indexed
    private String uploadedBy;

    private String fileName;

    private String originalFileName;

    private String contentType;

    private long fileSize;

    private MediaStatus status;

    private MediaMetadata metadata;

    private S3Location location;

    private LocalDateTime uploadedAt;

    private LocalDateTime processedAt;

    private boolean deleted;

    public enum MediaType {
        IMAGE,
        VIDEO,
        AUDIO,
        DOCUMENT
    }

    public enum MediaStatus {
        UPLOADING,
        UPLOADED,
        PROCESSING,
        PROCESSED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaMetadata {
        private int width;
        private int height;
        private long duration;
        private String format;
        private String codec;
        private int bitrate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class S3Location {
        private String bucket;
        private String key;
        private String url;
        private String thumbnailKey;
        private String thumbnailUrl;
    }
}
