package com.vito.checkin.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInEvent {
    
    private String eventId;
    
    private String userId;
    
    private String username;
    
    private String displayName;
    
    private String avatar;
    
    private LocalDateTime checkedInAt;
    
    private String location;
    
    private int totalCheckedIn;
    
    public static CheckInEvent fromTicket(CheckInTicket ticket, UserProfile profile, int totalCheckedIn) {
        return CheckInEvent.builder()
                .eventId(ticket.getEventId())
                .userId(ticket.getUserId())
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .avatar(profile.getAvatar())
                .checkedInAt(ticket.getCheckedInAt())
                .location(ticket.getCheckedInLocation())
                .totalCheckedIn(totalCheckedIn)
                .build();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfile {
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
    }
}
