package com.vito.checkin.domain.port.outbound;

import com.vito.checkin.domain.model.CheckInTicket;

import java.util.List;
import java.util.Optional;

public interface CheckInTicketRepository {
    
    CheckInTicket save(CheckInTicket ticket);
    
    Optional<CheckInTicket> findById(String ticketId);
    
    Optional<CheckInTicket> findByQrCode(String qrCode);
    
    Optional<CheckInTicket> findByEventIdAndUserId(String eventId, String userId);
    
    List<CheckInTicket> findByEventId(String eventId, int page, int size);
    
    List<CheckInTicket> findByEventIdAndStatus(String eventId, CheckInTicket.CheckInStatus status);
    
    long countByEventIdAndStatus(String eventId, CheckInTicket.CheckInStatus status);
    
    void delete(String ticketId);
    
    boolean existsByEventIdAndUserId(String eventId, String userId);
}
