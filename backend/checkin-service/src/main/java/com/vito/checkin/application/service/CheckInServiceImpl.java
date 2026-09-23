package com.vito.checkin.application.service;

import com.vito.checkin.domain.exception.CheckInException;
import com.vito.checkin.domain.model.CheckInEvent;
import com.vito.checkin.domain.model.CheckInTicket;
import com.vito.checkin.domain.port.inbound.CheckInService;
import com.vito.checkin.domain.port.outbound.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {
    
    private final CheckInTicketRepository ticketRepository;
    private final QRCodeService qrCodeService;
    private final CheckInEventPublisher eventPublisher;
    private final UserProfileService userProfileService;
    
    @Override
    public CheckInTicket generateTicket(GenerateTicketCommand command) {
        log.info("Generating check-in ticket for event: {} user: {}", 
                command.getEventId(), command.getUserId());
        
        // Check if user already has a ticket
        if (ticketRepository.existsByEventIdAndUserId(command.getEventId(), command.getUserId())) {
            throw CheckInException.alreadyHasTicket();
        }
        
        // Generate QR code
        String qrCode = qrCodeService.generateQRCode(command.getEventId(), command.getUserId());
        String qrCodeHash = qrCodeService.generateQRCodeHash(qrCode);
        
        CheckInTicket ticket = CheckInTicket.builder()
                .eventId(command.getEventId())
                .userId(command.getUserId())
                .qrCode(qrCode)
                .qrCodeHash(qrCodeHash)
                .status(CheckInTicket.CheckInStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        
        CheckInTicket savedTicket = ticketRepository.save(ticket);
        
        log.info("Check-in ticket generated: {} for event: {}", 
                savedTicket.getId(), command.getEventId());
        
        return savedTicket;
    }
    
    @Override
    public CheckInResult validateAndCheckIn(ValidateCheckInCommand command) {
        log.info("Validating check-in for event: {}", command.getEventId());
        
        // Find ticket by QR code
        CheckInTicket ticket = ticketRepository.findByQrCode(command.getQrCode())
                .orElseThrow(CheckInException::invalidQRCode);
        
        // Validate ticket belongs to the event
        if (!ticket.getEventId().equals(command.getEventId())) {
            throw CheckInException.invalidQRCode();
        }
        
        // Validate ticket is valid
        if (!ticket.isValid()) {
            if (ticket.getStatus() == CheckInTicket.CheckInStatus.USED) {
                throw CheckInException.ticketAlreadyUsed();
            } else if (ticket.getStatus() == CheckInTicket.CheckInStatus.EXPIRED) {
                throw CheckInException.ticketExpired();
            } else if (ticket.getStatus() == CheckInTicket.CheckInStatus.REVOKED) {
                throw CheckInException.ticketRevoked();
            }
        }
        
        // Validate QR code
        if (!qrCodeService.validateQRCode(command.getQrCode(), command.getEventId(), ticket.getUserId())) {
            throw CheckInException.invalidQRCode();
        }
        
        // Perform check-in
        ticket.checkIn(command.getLocation(), command.getLatitude(), command.getLongitude());
        CheckInTicket savedTicket = ticketRepository.save(ticket);
        
        // Get user profile for event
        CheckInEvent.UserProfile userProfile = userProfileService.getUserProfile(ticket.getUserId())
                .orElse(CheckInEvent.UserProfile.builder()
                        .userId(ticket.getUserId())
                        .username("unknown")
                        .displayName("Unknown User")
                        .build());
        
        // Get total checked in count
        int totalCheckedIn = (int) ticketRepository.countByEventIdAndStatus(
                command.getEventId(), CheckInTicket.CheckInStatus.USED);
        
        // Create check-in event
        CheckInEvent checkInEvent = CheckInEvent.fromTicket(savedTicket, userProfile, totalCheckedIn);
        
        // Publish real-time event
        eventPublisher.publishCheckIn(checkInEvent);
        eventPublisher.publishCheckInStats(command.getEventId(), totalCheckedIn, totalCheckedIn + 10);
        
        log.info("Check-in successful for event: {} user: {}", 
                command.getEventId(), ticket.getUserId());
        
        return CheckInResult.builder()
                .success(true)
                .message("Check-in successful")
                .ticket(savedTicket)
                .event(checkInEvent)
                .build();
    }
    
    @Override
    public CheckInTicket getTicket(String ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(CheckInException::ticketNotFound);
    }
    
    @Override
    public List<CheckInTicket> getEventTickets(String eventId, int page, int size) {
        return ticketRepository.findByEventId(eventId, page, size);
    }
    
    @Override
    public List<CheckInEvent> getRecentCheckIns(String eventId, int limit) {
        List<CheckInTicket> recentTickets = ticketRepository.findByEventIdAndStatus(
                eventId, CheckInTicket.CheckInStatus.USED);
        
        return recentTickets.stream()
                .limit(limit)
                .map(ticket -> {
                    CheckInEvent.UserProfile profile = userProfileService.getUserProfile(ticket.getUserId())
                            .orElse(CheckInEvent.UserProfile.builder()
                                    .userId(ticket.getUserId())
                                    .username("unknown")
                                    .displayName("Unknown User")
                                    .build());
                    
                    int totalCheckedIn = (int) ticketRepository.countByEventIdAndStatus(
                            eventId, CheckInTicket.CheckInStatus.USED);
                    
                    return CheckInEvent.fromTicket(ticket, profile, totalCheckedIn);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public CheckInStats getEventStats(String eventId) {
        long pending = ticketRepository.countByEventIdAndStatus(
                eventId, CheckInTicket.CheckInStatus.ACTIVE);
        long checkedIn = ticketRepository.countByEventIdAndStatus(
                eventId, CheckInTicket.CheckInStatus.USED);
        long expired = ticketRepository.countByEventIdAndStatus(
                eventId, CheckInTicket.CheckInStatus.EXPIRED);
        long revoked = ticketRepository.countByEventIdAndStatus(
                eventId, CheckInTicket.CheckInStatus.REVOKED);
        
        int totalExpected = (int) (pending + checkedIn + expired + revoked);
        double checkInRate = totalExpected > 0 ? (double) checkedIn / totalExpected * 100 : 0;
        
        return CheckInStats.builder()
                .eventId(eventId)
                .totalExpected(totalExpected)
                .totalCheckedIn((int) checkedIn)
                .totalPending((int) pending)
                .totalExpired((int) expired)
                .totalRevoked((int) revoked)
                .checkInRate(Math.round(checkInRate * 100.0) / 100.0)
                .build();
    }
    
    @Override
    public void revokeTicket(String ticketId, String userId) {
        log.info("Revoking ticket: {} by user: {}", ticketId, userId);
        
        CheckInTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(CheckInException::ticketNotFound);
        
        ticket.revoke();
        ticketRepository.save(ticket);
        
        log.info("Ticket revoked: {}", ticketId);
    }
}
