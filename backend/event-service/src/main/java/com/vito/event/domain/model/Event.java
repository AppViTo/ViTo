package com.vito.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder(toBuilder = true)
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
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    /**
     * ADR-001 / Principio IX: con {@code hideAddressUntilAccepted}, la dirección exacta solo
     * la ven el organizador y quien tiene RSVP {@code ACCEPTED}.
     */
    public boolean revealsExactAddressTo(String viewerId, boolean viewerAccepted) {
        if (settings == null || !settings.isHideAddressUntilAccepted()) {
            return true;
        }
        return viewerAccepted || (viewerId != null && viewerId.equals(organizerId));
    }
    
    /** Copia sin {@code address}, {@code latitude} ni {@code longitude}; se conserva {@code location}. */
    public Event withoutExactAddress() {
        if (details == null) {
            return this;
        }
        return toBuilder()
                .details(details.toBuilder().address(null).latitude(null).longitude(null).build())
                .build();
    }
    
    public enum EventStatus {
        DRAFT,
        PUBLISHED,
        CANCELLED,
        COMPLETED
    }
    
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetails {
        private Instant startDate;
        private Instant endDate;
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
        private boolean hideAddressUntilAccepted;
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
