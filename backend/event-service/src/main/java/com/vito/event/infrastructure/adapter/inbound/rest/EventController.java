package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    
    private final EventService eventService;
    
    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody EventService.CreateEventCommand command) {
        String currentUserId = getCurrentUserId();
        command.setOrganizerId(currentUserId);
        
        Event event = eventService.createEvent(command);
        
        return ResponseEntity.ok(event);
    }
    
    @PutMapping("/{eventId}")
    public ResponseEntity<Event> updateEvent(
            @PathVariable String eventId,
            @RequestBody EventService.UpdateEventCommand command) {
        String currentUserId = getCurrentUserId();
        command.setEventId(eventId);
        command.setUserId(currentUserId);
        
        Event event = eventService.updateEvent(command);
        
        return ResponseEntity.ok(event);
    }
    
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> cancelEvent(@PathVariable String eventId) {
        String currentUserId = getCurrentUserId();
        eventService.cancelEvent(eventId, currentUserId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{eventId}")
    public ResponseEntity<Event> getEvent(@PathVariable String eventId) {
        Event event = eventService.getEvent(eventId);
        return ResponseEntity.ok(event);
    }
    
    @GetMapping("/my-events")
    public ResponseEntity<List<Event>> getMyEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String currentUserId = getCurrentUserId();
        List<Event> events = eventService.getUserEvents(currentUserId, page, size);
        return ResponseEntity.ok(events);
    }
    
    @GetMapping("/upcoming")
    public ResponseEntity<List<Event>> getUpcomingEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Event> events = eventService.getUpcomingEvents(page, size);
        return ResponseEntity.ok(events);
    }
    
    @PostMapping("/{eventId}/rsvp")
    public ResponseEntity<RSVP> respondToRSVP(
            @PathVariable String eventId,
            @RequestBody EventService.RespondRSVPCommand command) {
        String currentUserId = getCurrentUserId();
        command.setEventId(eventId);
        command.setUserId(currentUserId);
        
        RSVP rsvp = eventService.respondToRSVP(command);
        
        return ResponseEntity.ok(rsvp);
    }
    
    @GetMapping("/{eventId}/attendees")
    public ResponseEntity<List<RSVP>> getEventAttendees(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<RSVP> attendees = eventService.getEventAttendees(eventId, page, size);
        return ResponseEntity.ok(attendees);
    }
    
    @PostMapping("/{eventId}/invitations")
    public ResponseEntity<Invitation> sendInvitation(
            @PathVariable String eventId,
            @RequestBody EventService.SendInvitationCommand command) {
        String currentUserId = getCurrentUserId();
        command.setEventId(eventId);
        command.setInviterId(currentUserId);
        
        Invitation invitation = eventService.sendInvitation(command);
        
        return ResponseEntity.ok(invitation);
    }
    
    @GetMapping("/{eventId}/invitations")
    public ResponseEntity<List<Invitation>> getEventInvitations(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Invitation> invitations = eventService.getEventInvitations(eventId, page, size);
        return ResponseEntity.ok(invitations);
    }
    
    @PostMapping("/{eventId}/invitations/respond")
    public ResponseEntity<Void> respondToInvitation(
            @PathVariable String eventId,
            @RequestBody EventService.RespondInvitationCommand command) {
        String currentUserId = getCurrentUserId();
        command.setEventId(eventId);
        command.setInviteeId(currentUserId);
        
        eventService.respondToInvitation(command);
        
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{eventId}/check-in")
    public ResponseEntity<RSVP> checkIn(
            @PathVariable String eventId,
            @RequestBody EventService.CheckInCommand command) {
        String currentUserId = getCurrentUserId();
        command.setEventId(eventId);
        command.setUserId(currentUserId);
        
        RSVP rsvp = eventService.checkIn(command);
        
        return ResponseEntity.ok(rsvp);
    }
    
    @GetMapping("/{eventId}/qr-code")
    public ResponseEntity<String> generateCheckInQR(@PathVariable String eventId) {
        String currentUserId = getCurrentUserId();
        String qrCode = eventService.generateCheckInQR(eventId, currentUserId);
        return ResponseEntity.ok(qrCode);
    }
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
