package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService;
import com.vito.event.domain.port.outbound.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    
    private final EventRepository eventRepository;
    private final RSVPRepository rsvpRepository;
    private final InvitationRepository invitationRepository;
    private final EventPublisherPort eventPublisher;
    private final QRCodeGenerator qrCodeGenerator;
    
    @Override
    public Event createEvent(CreateEventCommand command) {
        log.info("Creating event: {} by user: {}", command.getTitle(), command.getOrganizerId());
        
        Event event = Event.builder()
                .organizerId(command.getOrganizerId())
                .title(command.getTitle())
                .description(command.getDescription())
                .details(command.getDetails())
                .settings(command.getSettings() != null ? command.getSettings() : 
                        Event.EventSettings.builder()
                                .isPublic(true)
                                .requiresApproval(false)
                                .maxAttendees(100)
                                .allowGuestInvites(true)
                                .enableLiveStreaming(true)
                                .enableMosaic(true)
                                .build())
                .stats(Event.EventStats.builder().build())
                .status(Event.EventStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Event savedEvent = eventRepository.save(event);
        
        eventPublisher.publishEventCreated(savedEvent.getId(), savedEvent.getOrganizerId());
        
        log.info("Event created successfully: {}", savedEvent.getId());
        
        return savedEvent;
    }
    
    @Override
    public Event updateEvent(UpdateEventCommand command) {
        log.info("Updating event: {} by user: {}", command.getEventId(), command.getUserId());
        
        Event event = eventRepository.findById(command.getEventId())
                .orElseThrow(EventException::eventNotFound);
        
        if (!event.getOrganizerId().equals(command.getUserId())) {
            throw EventException.notAuthorized();
        }
        
        if (event.getStatus() == Event.EventStatus.CANCELLED) {
            throw EventException.eventAlreadyCancelled();
        }
        
        if (command.getTitle() != null) event.setTitle(command.getTitle());
        if (command.getDescription() != null) event.setDescription(command.getDescription());
        if (command.getDetails() != null) event.setDetails(command.getDetails());
        if (command.getSettings() != null) event.setSettings(command.getSettings());
        
        event.setUpdatedAt(LocalDateTime.now());
        
        Event updatedEvent = eventRepository.save(event);
        
        eventPublisher.publishEventUpdated(updatedEvent.getId(), updatedEvent.getOrganizerId());
        
        return updatedEvent;
    }
    
    @Override
    public void cancelEvent(String eventId, String userId) {
        log.info("Cancelling event: {} by user: {}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventException::eventNotFound);
        
        if (!event.getOrganizerId().equals(userId)) {
            throw EventException.notAuthorized();
        }
        
        event.setStatus(Event.EventStatus.CANCELLED);
        event.setUpdatedAt(LocalDateTime.now());
        
        eventRepository.save(event);
        
        eventPublisher.publishEventCancelled(eventId, userId);
    }
    
    @Override
    public Event getEvent(String eventId, String viewerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventException::eventNotFound);
        return forViewer(event, viewerId);
    }
    
    @Override
    public List<Event> getUserEvents(String userId, int page, int size) {
        return eventRepository.findByOrganizerId(userId, page, size);
    }
    
    @Override
    public List<Event> getUpcomingEvents(int page, int size, String viewerId) {
        return eventRepository.findUpcomingEvents(page, size).stream()
                .map(event -> forViewer(event, viewerId))
                .toList();
    }
    
    /** Oculta la dirección exacta si el evento lo pide y quien consulta no puede verla. */
    private Event forViewer(Event event, String viewerId) {
        // Solo se consulta el RSVP cuando el evento oculta la dirección y el viewer no es el organizador
        if (event.revealsExactAddressTo(viewerId, false)) {
            return event;
        }
        boolean accepted = viewerId != null && rsvpRepository.findByEventIdAndUserId(event.getId(), viewerId)
                .map(rsvp -> rsvp.getStatus() == RSVP.RSVPStatus.ACCEPTED)
                .orElse(false);
        return accepted ? event : event.withoutExactAddress();
    }
    
    @Override
    public RSVP respondToRSVP(RespondRSVPCommand command) {
        log.info("User {} responding to event {} with status: {}", 
                command.getUserId(), command.getEventId(), command.getStatus());
        
        Event event = eventRepository.findById(command.getEventId())
                .orElseThrow(EventException::eventNotFound);
        
        if (event.getOrganizerId().equals(command.getUserId())) {
            throw EventException.cannotRSVPToOwnEvent();
        }
        
        if (event.getStatus() == Event.EventStatus.CANCELLED) {
            throw EventException.eventAlreadyCancelled();
        }
        
        if (rsvpRepository.existsByEventIdAndUserId(command.getEventId(), command.getUserId())) {
            throw EventException.alreadyRSVP();
        }
        
        // Check capacity for ACCEPT status
        if (command.getStatus() == RSVP.RSVPStatus.ACCEPTED) {
            long acceptedCount = rsvpRepository.countByEventIdAndStatus(
                    command.getEventId(), RSVP.RSVPStatus.ACCEPTED);
            
            if (event.getSettings().getMaxAttendees() > 0 && 
                acceptedCount >= event.getSettings().getMaxAttendees()) {
                // Add to waitlist instead
                command.setStatus(RSVP.RSVPStatus.WAITLISTED);
            }
        }
        
        RSVP rsvp = RSVP.builder()
                .eventId(command.getEventId())
                .userId(command.getUserId())
                .status(command.getStatus())
                .message(command.getMessage())
                .guestsCount(command.getGuestsCount())
                .respondedAt(LocalDateTime.now())
                .build();
        
        // Generate QR code for accepted RSVPs
        if (command.getStatus() == RSVP.RSVPStatus.ACCEPTED) {
            String qrCode = qrCodeGenerator.generateCheckInQR(command.getEventId(), command.getUserId());
            rsvp.setCheckInQrCode(qrCode);
        }
        
        RSVP savedRsvp = rsvpRepository.save(rsvp);
        
        // Update event stats
        updateEventStats(command.getEventId(), command.getStatus(), true);
        
        eventPublisher.publishRSVPChanged(command.getEventId(), command.getUserId(), 
                command.getStatus().name());
        
        return savedRsvp;
    }
    
    @Override
    public List<RSVP> getEventAttendees(String eventId, int page, int size) {
        return rsvpRepository.findByEventId(eventId, page, size);
    }
    
    @Override
    public Invitation sendInvitation(SendInvitationCommand command) {
        log.info("Sending invitation to {} for event {} by user: {}", 
                command.getInviteeId(), command.getEventId(), command.getInviterId());
        
        Event event = eventRepository.findById(command.getEventId())
                .orElseThrow(EventException::eventNotFound);
        
        if (!event.getOrganizerId().equals(command.getInviterId())) {
            throw EventException.notAuthorized();
        }
        
        if (invitationRepository.existsByEventIdAndInviteeId(command.getEventId(), command.getInviteeId())) {
            throw EventException.alreadyInvited();
        }
        
        Invitation invitation = Invitation.builder()
                .eventId(command.getEventId())
                .inviterId(command.getInviterId())
                .inviteeId(command.getInviteeId())
                .status(Invitation.InvitationStatus.PENDING)
                .message(command.getMessage())
                .sentAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        
        Invitation savedInvitation = invitationRepository.save(invitation);
        
        // Update event stats
        Event eventStats = eventRepository.findById(command.getEventId()).orElse(event);
        eventStats.getStats().setTotalInvited(eventStats.getStats().getTotalInvited() + 1);
        eventRepository.save(eventStats);
        
        eventPublisher.publishInvitationSent(command.getEventId(), command.getInviterId(), 
                command.getInviteeId());
        
        return savedInvitation;
    }
    
    @Override
    public List<Invitation> getEventInvitations(String eventId, int page, int size) {
        return invitationRepository.findByEventId(eventId, page, size);
    }
    
    @Override
    public void respondToInvitation(RespondInvitationCommand command) {
        log.info("User {} responding to invitation for event {} with status: {}", 
                command.getInviteeId(), command.getEventId(), command.getStatus());
        
        Invitation invitation = invitationRepository.findByEventIdAndInviteeId(
                command.getEventId(), command.getInviteeId())
                .orElseThrow(EventException::invitationNotFound);
        
        if (invitation.isExpired()) {
            throw EventException.invitationExpired();
        }
        
        if (command.getStatus() == Invitation.InvitationStatus.ACCEPTED) {
            invitation.accept();
            
            // Auto-create RSVP
            RSVP rsvp = RSVP.builder()
                    .eventId(command.getEventId())
                    .userId(command.getInviteeId())
                    .status(RSVP.RSVPStatus.ACCEPTED)
                    .respondedAt(LocalDateTime.now())
                    .build();
            
            String qrCode = qrCodeGenerator.generateCheckInQR(command.getEventId(), command.getInviteeId());
            rsvp.setCheckInQrCode(qrCode);
            
            rsvpRepository.save(rsvp);
            updateEventStats(command.getEventId(), RSVP.RSVPStatus.ACCEPTED, true);
        } else {
            invitation.decline();
        }
        
        invitationRepository.save(invitation);
    }
    
    @Override
    public String generateCheckInQR(String eventId, String userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventException::eventNotFound);
        
        if (!event.getOrganizerId().equals(userId)) {
            throw EventException.notAuthorized();
        }
        
        return qrCodeGenerator.generateCheckInQR(eventId, userId);
    }
    
    @Override
    public RSVP checkIn(CheckInCommand command) {
        log.info("Check-in attempt for event {} by user: {}", command.getEventId(), command.getUserId());
        
        Event event = eventRepository.findById(command.getEventId())
                .orElseThrow(EventException::eventNotFound);
        
        RSVP rsvp = rsvpRepository.findByEventIdAndUserId(command.getEventId(), command.getUserId())
                .orElseThrow(EventException::invalidCheckIn);
        
        if (rsvp.isCheckedIn()) {
            throw EventException.alreadyCheckedIn();
        }
        
        if (!qrCodeGenerator.validateCheckInQR(command.getQrCode(), command.getEventId(), command.getUserId())) {
            throw EventException.invalidCheckIn();
        }
        
        rsvp.checkIn();
        RSVP savedRsvp = rsvpRepository.save(rsvp);
        
        // Update event stats
        Event eventStats = eventRepository.findById(command.getEventId()).orElse(event);
        eventStats.getStats().setTotalCheckedIn(eventStats.getStats().getTotalCheckedIn() + 1);
        eventRepository.save(eventStats);
        
        eventPublisher.publishCheckIn(command.getEventId(), command.getUserId());
        
        return savedRsvp;
    }
    
    private void updateEventStats(String eventId, RSVP.RSVPStatus status, boolean increment) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return;
        
        Event.EventStats stats = event.getStats();
        int delta = increment ? 1 : -1;
        
        switch (status) {
            case ACCEPTED -> stats.setTotalAccepted(stats.getTotalAccepted() + delta);
            case DECLINED -> stats.setTotalDeclined(stats.getTotalDeclined() + delta);
            case MAYBE -> stats.setTotalMaybe(stats.getTotalMaybe() + delta);
            case WAITLISTED -> stats.setTotalWaitlisted(stats.getTotalWaitlisted() + delta);
        }
        
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }
}
