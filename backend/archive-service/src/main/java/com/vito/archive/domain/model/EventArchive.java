package com.vito.archive.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventArchive {
    private String id;
    private String eventId;
    private String organizerId;
    private String title;
    private String? description;
    private String? coverUrl;
    private ArchiveStatus status;
    private EventStatistics statistics;
    private SharingSettings sharing;
    private LocalDateTime eventDate;
    private LocalDateTime? archivedAt;
    private LocalDateTime createdAt;
    
    public enum ArchiveStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventStatistics {
        private int totalAttendees;
        private int totalCheckIns;
        private int totalPhotos;
        private int totalReactions;
        private int totalComments;
        private int totalShares;
        private int peakConcurrentViewers;
        private int totalStreamMinutes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharingSettings {
        private boolean isPublic;
        private boolean allowComments;
        private boolean allowReactions;
        private boolean allowSharing;
        private String shareUrl;
        private String shareCode;
    }
}
