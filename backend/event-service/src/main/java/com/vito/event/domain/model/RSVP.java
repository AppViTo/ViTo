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
public class RSVP {
    
    private String id;
    
    private String eventId;
    
    private String userId;
    
    /** Nombre de quien responde, desde {@code X-User-Name} (R6); null en respuestas anteriores. */
    private String displayName;
    
    private RSVPStatus status;
    
    private String message;
    
    // Instantes UTC (FR-028). guestsCount salió del contrato en la Ola 1 (R2)
    private Instant respondedAt;
    
    /** Orden de la lista de espera: se fija al pasar a {@code WAITLISTED}. */
    private Instant waitlistedAt;
    
    private Instant checkedInAt;
    
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
        this.checkedInAt = Instant.now();
    }
}
