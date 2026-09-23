package com.vito.event.domain.model;

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
public class Event {
    
    private String id;
    
    private String organizerId;
    
    private String title;
    
    private String description;
    
    private EventDetails details;
    
    private EventSettings settings;
    
    private EventStats stats;
    
    private EventStatus status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public enum EventStatus {
        DRAFT,
        PUBLISHED,
        CANCELLED,
        COMPLETED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetails {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String location;
        private String address;
        private Double latitude;
        private Double longitude;
        private List<String> coverImages;
        private List<String> tags;
        private String category;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventSettings {
        private boolean isPublic;
        private boolean requiresApproval;
        private int maxAttendees;
        private boolean allowGuestInvites;
        private boolean enableLiveStreaming;
        private boolean enableMosaic;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventStats {
        private int totalInvited;
        private int totalAccepted;
        private int totalDeclined;
        private int totalMaybe;
        private int totalWaitlisted;
        private int totalCheckedIn;
        private int totalViewers;
    }
}
