package com.vito.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {
    
    private String id;
    
    private String eventId;
    
    private String inviterId;
    
    private String inviteeId;
    
    private InvitationStatus status;
    
    private String message;
    
    /** Enlace por el que se unió la persona; null si la invitación se envió por la app. */
    private String inviteLinkId;
    
    // Instantes UTC, como Event desde Bug-B01 (FR-028)
    private Instant sentAt;
    
    /** {@code sentAt} + 7 días por la app; null por enlace (ya aceptada). */
    private Instant expiresAt;
    
    private Instant respondedAt;
    
    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }
    
    public void decline() {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = Instant.now();
    }
}
