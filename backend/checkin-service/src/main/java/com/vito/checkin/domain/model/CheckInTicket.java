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
public class CheckInTicket {
    
    private String id;
    
    private String eventId;
    
    private String userId;
    
    private String qrCode;
    
    private String qrCodeHash;
    
    private CheckInStatus status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime expiresAt;
    
    private LocalDateTime checkedInAt;
    
    private String checkedInLocation;
    
    private Double checkedInLatitude;
    
    private Double checkedInLongitude;
    
    public enum CheckInStatus {
        PENDING,
        ACTIVE,
        USED,
        EXPIRED,
        REVOKED
    }
    
    public boolean isValid() {
        return status == CheckInStatus.ACTIVE && 
               (expiresAt == null || LocalDateTime.now().isBefore(expiresAt));
    }
    
    public void checkIn(String location, Double latitude, Double longitude) {
        this.status = CheckInStatus.USED;
        this.checkedInAt = LocalDateTime.now();
        this.checkedInLocation = location;
        this.checkedInLatitude = latitude;
        this.checkedInLongitude = longitude;
    }
    
    public void revoke() {
        this.status = CheckInStatus.REVOKED;
    }
    
    public void expire() {
        this.status = CheckInStatus.EXPIRED;
    }
}
