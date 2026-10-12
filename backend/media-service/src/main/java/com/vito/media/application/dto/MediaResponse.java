package com.vito.media.application.dto;

import com.vito.media.domain.model.Media;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record MediaResponse(
        String id,
        String eventId,
        String uploadedBy,
        String fileName,
        String originalFileName,
        String contentType,
        long fileSize,
        Media.MediaStatus status,
        Media.MediaMetadata metadata,
        Media.S3Location location,
        LocalDateTime uploadedAt,
        LocalDateTime processedAt
) {
    public static MediaResponse from(Media media) {
        return new MediaResponse(
                media.getId(),
                media.getEventId(),
                media.getUploadedBy(),
                media.getFileName(),
                media.getOriginalFileName(),
                media.getContentType(),
                media.getFileSize(),
                media.getStatus(),
                media.getMetadata(),
                media.getLocation(),
                media.getUploadedAt(),
                media.getProcessedAt()
        );
    }
}
