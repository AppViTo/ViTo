package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService;
import com.vito.event.domain.port.outbound.*;
import com.vito.event.domain.port.outbound.EventRepository.StatCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    
    /** Máximo de elementos por página (CONVENTIONS §5); {@code size} &lt; 1 sería {@code limit(0)}, sin límite en Mongo. */
    static final int MAX_PAGE_SIZE = 100;
    
    private final EventRepository eventRepository;
    private final RSVPRepository rsvpRepository;
    private final InvitationRepository invitationRepository;
    private final EventPublisherPort eventPublisher;
    private final QRCodeGenerator qrCodeGenerator;

    /** Contrato: {@code RespondRsvpRequest.message} y {@code Rsvp.message}, maxLength 280. */
    static final int MAX_RSVP_MESSAGE = 280;
    private final AttendanceService attendanceService;
    
    @Override
    public Event createEvent(CreateEventCommand command) {
        log.info("Creating event: {} by user: {}", command.getTitle(), command.getOrganizerId());
        
        Event event = Event.builder()
                .organizerId(command.getOrganizerId())
                .organizerDisplayName(command.getOrganizerDisplayName())
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
                .status(Event.EventStatus.PUBLISHED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
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
        // R6: el nombre vigente del organizador; sin X-User-Name se conserva el guardado
        if (command.getOrganizerDisplayName() != null) {
            event.setOrganizerDisplayName(command.getOrganizerDisplayName());
        }

        event.setUpdatedAt(Instant.now());
        
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
        event.setUpdatedAt(Instant.now());
        
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
    public List<Event> listEvents(ListScope scope, String viewerId, int page, int size) {
        // A diferencia de /my-events y /upcoming, listEvents rechaza size > 100 (ADR-014)
        if (size > MAX_PAGE_SIZE) {
            throw EventException.pageSizeTooLarge(MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw EventException.negativePage();
        }
        List<Event> events = switch (scope) {
            case MINE -> eventRepository.findByOrganizerId(viewerId, page, pageSize(size));
            case UPCOMING -> eventRepository.findUpcomingEvents(page, pageSize(size));
            case INVITED -> invitedEvents(viewerId, page, pageSize(size));
        };
        return events.stream()
                .map(event -> forViewer(event, viewerId))
                .toList();
    }

    /** R10: invitaciones vigentes o aceptadas y RSVP distintos de "no voy", sin repetir eventos. */
    private List<Event> invitedEvents(String viewerId, int page, int size) {
        Instant now = Instant.now();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(invitationRepository.findEventIdsInviting(viewerId, now));
        ids.addAll(rsvpRepository.findEventIdsAnsweredBy(viewerId));
        return eventRepository.findActiveByIds(ids, now, page, size);
    }

    @Override
    public List<Event> getUserEvents(String userId, int page, int size) {
        return eventRepository.findByOrganizerId(userId, page, pageSize(size));
    }
    
    @Override
    public List<Event> getUpcomingEvents(int page, int size, String viewerId) {
        return eventRepository.findUpcomingEvents(page, pageSize(size)).stream()
                .map(event -> forViewer(event, viewerId))
                .toList();
    }
    
    private static int pageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
    
    /**
     * Vista según quién consulta. El organizador ve todo. Los demás reciben {@code stats} con solo
     * {@code totalAccepted} (R7) y, si el evento lo pide, sin dirección exacta hasta confirmar.
     */
    private Event forViewer(Event event, String viewerId) {
        if (viewerId != null && viewerId.equals(event.getOrganizerId())) {
            return event;
        }
        Event view = event.withAcceptedCountOnly();
        // Solo se consulta el RSVP cuando el evento oculta la dirección
        if (view.revealsExactAddressTo(viewerId, false)) {
            return view;
        }
        boolean accepted = viewerId != null && rsvpRepository.findByEventIdAndUserId(event.getId(), viewerId)
                .map(rsvp -> rsvp.getStatus() == RSVP.RSVPStatus.ACCEPTED)
                .orElse(false);
        return accepted ? view : view.withoutExactAddress();
    }
    
    /**
     * T030 (US3): upsert de la respuesta, único camino para responder una invitación (FR-017).
     * Repetir la misma respuesta no cambia nada (FR-019). Ya no genera QR: pasa a
     * checkin-service en la Ola 2.
     */
    @Override
    public RSVP respondToRSVP(RespondRSVPCommand command) {
        RSVP.RSVPStatus answer = command.getStatus();
        if (answer != RSVP.RSVPStatus.ACCEPTED && answer != RSVP.RSVPStatus.DECLINED
                && answer != RSVP.RSVPStatus.MAYBE) {
            throw EventException.invalidRsvpStatus();
        }
        if (command.getMessage() != null && command.getMessage().length() > MAX_RSVP_MESSAGE) {
            throw EventException.rsvpMessageTooLong(MAX_RSVP_MESSAGE);
        }
        String eventId = command.getEventId();
        String userId = command.getUserId();

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventException::eventNotFound);
        if (event.getOrganizerId().equals(userId)) {
            throw EventException.cannotRSVPToOwnEvent();
        }
        Optional<Invitation> invitation = invitationRepository.findByEventIdAndInviteeId(eventId, userId);
        if (!canRespond(event, userId, invitation)) {
            // R3: el mismo 404 que un evento inexistente, sin revelar que existe
            throw EventException.eventNotFound();
        }
        if (event.getStatus() == Event.EventStatus.CANCELLED) {
            throw EventException.eventAlreadyCancelled();
        }
        if (!event.isActiveAt(Instant.now())) {
            throw EventException.rsvpClosed();
        }

        // Cupo atómico (R1): AttendanceService reserva el lugar o deja la respuesta en lista de espera
        RSVP saved = answer == RSVP.RSVPStatus.ACCEPTED
                ? attendanceService.confirm(eventId, userId, command.getDisplayName(), command.getMessage())
                : attendanceService.release(eventId, userId, answer, command.getDisplayName(), command.getMessage());
        invitation.ifPresent(inv -> syncInvitation(inv, answer));

        eventPublisher.publishRSVPChanged(eventId, userId, saved.getStatus().name());
        log.info("RSVP {} en el evento {}", saved.getStatus(), eventId);
        return saved;
    }

    /**
     * R3: público; invitación vigente o ya aceptada; o un RSVP previo aunque la invitación
     * haya vencido (aclaración del 2026-11-01).
     */
    private boolean canRespond(Event event, String userId, Optional<Invitation> invitation) {
        if (event.getSettings() != null && event.getSettings().isPublic()) {
            return true;
        }
        boolean validInvitation = invitation
                .filter(inv -> inv.getStatus() != Invitation.InvitationStatus.EXPIRED)
                .filter(inv -> inv.getStatus() == Invitation.InvitationStatus.ACCEPTED || !inv.isExpired())
                .isPresent();
        return validInvitation || rsvpRepository.existsByEventIdAndUserId(event.getId(), userId);
    }

    /** FR-017: "voy" (con lugar o en espera) acepta, "no voy" rechaza, "tal vez" la deja pendiente. */
    private void syncInvitation(Invitation invitation, RSVP.RSVPStatus answer) {
        Invitation.InvitationStatus target = switch (answer) {
            case ACCEPTED -> Invitation.InvitationStatus.ACCEPTED;
            case DECLINED -> Invitation.InvitationStatus.DECLINED;
            default -> Invitation.InvitationStatus.PENDING;
        };
        if (invitation.getStatus() == target) {
            return;
        }
        invitation.setStatus(target);
        invitation.setRespondedAt(target == Invitation.InvitationStatus.PENDING ? null : Instant.now());
        invitationRepository.save(invitation);
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
                .sentAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        Invitation savedInvitation = invitationRepository.save(invitation);

        eventRepository.incrementStat(command.getEventId(), StatCounter.INVITED, 1);
        
        eventPublisher.publishInvitationSent(command.getEventId(), command.getInviterId(), 
                command.getInviteeId());
        
        return savedInvitation;
    }
    
    @Override
    public List<Invitation> getEventInvitations(String eventId, int page, int size) {
        return invitationRepository.findByEventId(eventId, page, size);
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
        
        eventRepository.incrementStat(command.getEventId(), StatCounter.CHECKED_IN, 1);
        
        eventPublisher.publishCheckIn(command.getEventId(), command.getUserId());
        
        return savedRsvp;
    }
}
