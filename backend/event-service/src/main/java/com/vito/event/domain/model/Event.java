package com.vito.event.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    
    /** Nombre del organizador al crear o editar, desde {@code X-User-Name} (R6); null en eventos anteriores. */
    private String organizerDisplayName;
    
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
    
    /** Publicado y sin terminar: admite respuestas e invitados (R3, FR-007). */
    public boolean isActiveAt(Instant now) {
        return status == EventStatus.PUBLISHED
                && (details == null || details.getEndDate() == null || details.getEndDate().isAfter(now));
    }

    /** Copia con {@code stats} reducido a {@code totalAccepted} (R7); nunca se guarda. */
    public Event withAcceptedCountOnly() {
        return toBuilder().stats(stats == null ? null : stats.acceptedOnly()).build();
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventStats {
        // Integer y no int: la vista de quien no organiza omite los demás contadores (R7)
        @Builder.Default
        private Integer totalInvited = 0;
        @Builder.Default
        private Integer totalAccepted = 0;
        @Builder.Default
        private Integer totalDeclined = 0;
        @Builder.Default
        private Integer totalMaybe = 0;
        @Builder.Default
        private Integer totalWaitlisted = 0;
        @Builder.Default
        private Integer totalCheckedIn = 0;
        @Builder.Default
        private Integer totalViewers = 0;

        /** Solo {@code totalAccepted}: lo que ve quien no organiza (R7, FR-024a). */
        public EventStats acceptedOnly() {
            return new EventStats(null, totalAccepted, null, null, null, null, null);
        }
    }
}
