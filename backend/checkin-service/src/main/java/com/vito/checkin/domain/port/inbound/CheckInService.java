package com.vito.checkin.domain.port.inbound;

import com.vito.checkin.domain.model.CheckInEvent;
import com.vito.checkin.domain.model.CheckInTicket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface CheckInService {
    
    CheckInTicket generateTicket(GenerateTicketCommand command);
    
    CheckInResult validateAndCheckIn(ValidateCheckInCommand command);
    
    CheckInTicket getTicket(String ticketId);
    
    List<CheckInTicket> getEventTickets(String eventId, int page, int size);
    
    List<CheckInEvent> getRecentCheckIns(String eventId, int limit);
    
    CheckInStats getEventStats(String eventId);
    
    void revokeTicket(String ticketId, String userId);
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class GenerateTicketCommand {
        private String eventId;
        private String userId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ValidateCheckInCommand {
        private String qrCode;
        private String eventId;
        private String location;
        private Double latitude;
        private Double longitude;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CheckInResult {
        private boolean success;
        private String message;
        private CheckInTicket ticket;
        private CheckInEvent event;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CheckInStats {
        private String eventId;
        private int totalExpected;
        private int totalCheckedIn;
        private int totalPending;
        private int totalExpired;
        private int totalRevoked;
        private double checkInRate;
    }
}
