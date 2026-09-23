package com.vito.checkin.infrastructure.adapter.inbound.rest;

import com.vito.checkin.domain.model.CheckInEvent;
import com.vito.checkin.domain.model.CheckInTicket;
import com.vito.checkin.domain.port.inbound.CheckInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
public class CheckInController {
    
    private final CheckInService checkInService;
    
    @PostMapping("/ticket")
    public ResponseEntity<CheckInTicket> generateTicket(
            @RequestParam String eventId) {
        String currentUserId = getCurrentUserId();
        
        CheckInTicket ticket = checkInService.generateTicket(
                CheckInService.GenerateTicketCommand.builder()
                        .eventId(eventId)
                        .userId(currentUserId)
                        .build());
        
        return ResponseEntity.ok(ticket);
    }
    
    @PostMapping("/validate")
    public ResponseEntity<CheckInService.CheckInResult> validateAndCheckIn(
            @RequestBody CheckInService.ValidateCheckInCommand command) {
        
        CheckInService.CheckInResult result = checkInService.validateAndCheckIn(command);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<CheckInTicket> getTicket(@PathVariable String ticketId) {
        CheckInTicket ticket = checkInService.getTicket(ticketId);
        return ResponseEntity.ok(ticket);
    }
    
    @GetMapping("/event/{eventId}/tickets")
    public ResponseEntity<List<CheckInTicket>> getEventTickets(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<CheckInTicket> tickets = checkInService.getEventTickets(eventId, page, size);
        return ResponseEntity.ok(tickets);
    }
    
    @GetMapping("/event/{eventId}/recent")
    public ResponseEntity<List<CheckInEvent>> getRecentCheckIns(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<CheckInEvent> recentCheckIns = checkInService.getRecentCheckIns(eventId, limit);
        return ResponseEntity.ok(recentCheckIns);
    }
    
    @GetMapping("/event/{eventId}/stats")
    public ResponseEntity<CheckInService.CheckInStats> getEventStats(
            @PathVariable String eventId) {
        
        CheckInService.CheckInStats stats = checkInService.getEventStats(eventId);
        return ResponseEntity.ok(stats);
    }
    
    @DeleteMapping("/ticket/{ticketId}")
    public ResponseEntity<Void> revokeTicket(@PathVariable String ticketId) {
        String currentUserId = getCurrentUserId();
        checkInService.revokeTicket(ticketId, currentUserId);
        return ResponseEntity.noContent().build();
    }
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
