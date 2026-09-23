package com.vito.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RSVP {
    
    private String id;
    
    private String eventId;
    
    private String userId;
    
    private RSVPStatus status;
    
    private String message;
    
    private int guestsCount;
    
    private LocalDateTime respondedAt;
    
    private LocalDateTime checkedInAt;
    
    private String checkInQrCode;
    
    public enum RSVPStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        MAYBE,
        WAITLISTED
    }
    
    public boolean isCheckedIn() {
        return checkedInAt != null;
    }
    
    public void checkIn() {
        this.checkedInAt = LocalDateTime.now();
    }
}
