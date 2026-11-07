package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService;
import com.vito.event.domain.port.outbound.*;
import com.vito.event.domain.port.outbound.EventRepository.StatCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    
    /** Máximo de elementos por página (CONVENTIONS §5); {@code size} &lt; 1 sería {@code limit(0)}, sin límite en Mongo. */
    static final int MAX_PAGE_SIZE = 100;
    /** FR-010. */
    static final int MAX_INVITATION_MESSAGE = 280;
    static final Duration INVITATION_TTL = Duration.ofDays(7);
    
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
            throw EventException.onlyOrganizerCanEdit();
        }

        EventPatch patch = command.getPatch();
        if (patch == null || patch.isEmpty()) {
            throw EventException.emptyPatch();
        }
        validate(patch, event);

        Instant now = Instant.now();
        if (!event.isEditableAt(now)) {
            throw EventException.eventNotEditable();
        }
        int accepted = acceptedCount(event);
        Integer newCapacity = patch.maxAttendees() != null ? patch.maxAttendees().value() : null;
        if (newCapacity != null && newCapacity > 0 && newCapacity < accepted) {
            throw EventException.capacityBelowAccepted(accepted);
        }

        // R6: el nombre vigente del organizador; sin nombre se conserva el guardado
        Event updated = eventRepository.applyPatch(event.getId(), patch, command.getOrganizerDisplayName(), now)
                .orElseThrow(() -> conflictAfterRace(event.getId(), newCapacity, now));

        eventPublisher.publishEventUpdated(updated.getId(), updated.getOrganizerId());

        // Cupo más alto (o sin límite): la lista de espera pasa a confirmados por orden de llegada
        int oldCapacity = event.getSettings() != null ? event.getSettings().getMaxAttendees() : 0;
        if (newCapacity != null && oldCapacity != 0 && (newCapacity == 0 || newCapacity > oldCapacity)) {
            if (attendanceService.fillFreedSeats(updated.getId()) > 0) {
                updated = eventRepository.findById(updated.getId()).orElse(updated);
            }
        }
        log.info("Event {} updated", updated.getId());
        return updated;
    }

    /** Límites de UpdateEventRequest: los mismos que createEvent (T-B29) más los de details/settings. */
    static final int MAX_TITLE = 100;
    static final int MIN_TITLE = 3;
    static final int MAX_DESCRIPTION = 2000;
    static final int MAX_PLACE = 300;
    static final java.util.Set<String> CATEGORIES =
            java.util.Set.of("BIRTHDAY", "MEETING", "WEDDING", "GRADUATION", "TRAVEL", "PARTY");

    private static void validate(EventPatch patch, Event current) {
        if (patch.title() != null) {
            String title = patch.title().value();
            if (title == null || title.isBlank()) {
                throw EventException.invalidField("title", "Es obligatorio");
            }
            if (title.strip().length() < MIN_TITLE || title.length() > MAX_TITLE) {
                throw EventException.invalidField("title", "Entre " + MIN_TITLE + " y " + MAX_TITLE + " caracteres");
            }
        }
        maxLength(patch.description(), "description", MAX_DESCRIPTION);
        maxLength(patch.location(), "details.location", MAX_PLACE);
        maxLength(patch.address(), "details.address", MAX_PLACE);
        if (patch.category() != null && patch.category().value() != null
                && !CATEGORIES.contains(patch.category().value())) {
            throw EventException.invalidField("details.category",
                    "Valores permitidos: BIRTHDAY, MEETING, WEDDING, GRADUATION, TRAVEL, PARTY");
        }
        range(patch.latitude(), "details.latitude", 90);
        range(patch.longitude(), "details.longitude", 180);
        if (patch.maxAttendees() != null) {
            Integer max = patch.maxAttendees().value();
            if (max == null || max < 0) {
                throw EventException.invalidField("settings.maxAttendees", "Mínimo 0 (0 = sin límite)");
            }
        }
        for (var flag : java.util.Map.of(
                "settings.isPublic", java.util.Optional.ofNullable(patch.isPublic()),
                "settings.requiresApproval", java.util.Optional.ofNullable(patch.requiresApproval()),
                "settings.allowGuestInvites", java.util.Optional.ofNullable(patch.allowGuestInvites()),
                "settings.enableLiveStreaming", java.util.Optional.ofNullable(patch.enableLiveStreaming()),
                "settings.enableMosaic", java.util.Optional.ofNullable(patch.enableMosaic()),
                "settings.hideAddressUntilAccepted", java.util.Optional.ofNullable(patch.hideAddressUntilAccepted()))
                .entrySet()) {
            if (flag.getValue().isPresent() && flag.getValue().get().value() == null) {
                throw EventException.invalidField(flag.getKey(), "Debe ser true o false");
            }
        }
        // endDate > startDate con las fechas que quedan tras el parche
        Event.EventDetails details = current.getDetails();
        Instant start = EventPatch.valueOr(patch.startDate(), details != null ? details.getStartDate() : null);
        Instant end = EventPatch.valueOr(patch.endDate(), details != null ? details.getEndDate() : null);
        if (start != null && end != null && !end.isAfter(start)) {
            throw EventException.invalidField(patch.endDate() != null ? "details.endDate" : "details.startDate",
                    "endDate debe ser posterior a startDate");
        }
    }

    private static void maxLength(EventPatch.Change<String> change, String field, int max) {
        if (change != null && change.value() != null && change.value().length() > max) {
            throw EventException.invalidField(field, "Máximo " + max + " caracteres");
        }
    }

    private static void range(EventPatch.Change<Double> change, String field, double limit) {
        if (change != null && change.value() != null && Math.abs(change.value()) > limit) {
            throw EventException.invalidField(field, "Entre -" + (int) limit + " y " + (int) limit);
        }
    }

    private static int acceptedCount(Event event) {
        Integer accepted = event.getStats() != null ? event.getStats().getTotalAccepted() : null;
        return accepted != null ? accepted : 0;
    }

    /** {@code applyPatch} no actualizó: alguien canceló el evento o confirmó entre la lectura y la escritura. */
    private EventException conflictAfterRace(String eventId, Integer newCapacity, Instant now) {
        Event latest = eventRepository.findById(eventId).orElseThrow(EventException::eventNotFound);
        if (!latest.isEditableAt(now)) {
            return EventException.eventNotEditable();
        }
        return EventException.capacityBelowAccepted(acceptedCount(latest));
    }
    
    @Override
    public void cancelEvent(String eventId, String userId) {
        log.info("Cancelling event: {} by user: {}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventException::eventNotFound);
        
        if (!event.getOrganizerId().equals(userId)) {
            throw EventException.onlyOrganizerCanCancel();
        }
        // FR-022: cancelar dos veces no es un error ni vuelve a avisar
        if (event.getStatus() == Event.EventStatus.CANCELLED) {
            return;
        }
        Instant now = Instant.now();
        if (hasEnded(event, now)) {
            throw EventException.eventAlreadyEnded();
        }

        // $set condicional: no reescribe stats ni toca invitaciones, RSVP ni enlaces
        if (!eventRepository.cancel(eventId, now)) {
            // Cambió entre la lectura y la escritura: otra cancelación (204) o terminó (409)
            Event latest = eventRepository.findById(eventId).orElseThrow(EventException::eventNotFound);
            if (latest.getStatus() == Event.EventStatus.CANCELLED) {
                return;
            }
            throw EventException.eventAlreadyEnded();
        }
        eventPublisher.publishEventCancelled(eventId, userId);
        log.info("Event {} cancelled", eventId);
    }

    /** {@code COMPLETED} o con {@code endDate} ya pasada. */
    private static boolean hasEnded(Event event, Instant now) {
        Instant end = event.getDetails() != null ? event.getDetails().getEndDate() : null;
        return event.getStatus() == Event.EventStatus.COMPLETED || (end != null && !end.isAfter(now));
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
    
    /** US6 (FR-024): solo el organizador, aunque otra persona esté confirmada. */
    @Override
    public List<RSVP> getEventAttendees(String eventId, String userId, RSVP.RSVPStatus status, int page, int size) {
        checkPage(page, size);
        organizedEvent(eventId, userId, EventException::eventNotFound, EventException::onlyOrganizerCanListAttendees);
        return rsvpRepository.findByEventId(eventId, status, page, Math.max(1, size));
    }

    private static void checkPage(int page, int size) {
        if (size > MAX_PAGE_SIZE) {
            throw EventException.linkPageSizeTooLarge(MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw EventException.negativePage();
        }
    }

    private Event organizedEvent(String eventId, String userId, Supplier<EventException> notFound,
                                 Supplier<EventException> notOrganizer) {
        Event event = eventRepository.findById(eventId).orElseThrow(notFound);
        if (!event.getOrganizerId().equals(userId)) {
            throw notOrganizer.get();
        }
        return event;
    }
    
    @Override
    public Invitation sendInvitation(SendInvitationCommand command) {
        log.info("Sending invitation to {} for event {} by user: {}", 
                command.getInviteeId(), command.getEventId(), command.getInviterId());
        
        // US4: 404 → 403 → 400 → 409, como editar y cancelar
        Event event = organizedEvent(command.getEventId(), command.getInviterId(),
                EventException::eventOrUserNotFound, EventException::onlyOrganizerCanInvite);

        String inviteeId = command.getInviteeId() == null ? "" : command.getInviteeId().strip();
        if (inviteeId.isEmpty()) {
            throw EventException.inviteeRequired();
        }
        if (inviteeId.equals(command.getInviterId())) {
            throw EventException.cannotInviteYourself();
        }
        String message = command.getMessage();
        if (message != null && message.codePointCount(0, message.length()) > MAX_INVITATION_MESSAGE) {
            throw EventException.invitationMessageTooLong(MAX_INVITATION_MESSAGE);
        }

        Instant now = Instant.now();
        // US5: un evento cancelado o terminado no admite invitados (sendInvitation 409)
        if (!event.isActiveAt(now)) {
            throw EventException.eventNotAcceptingGuests();
        }

        // FR-011: también si antes rechazó o se venció; el índice único cubre la carrera
        if (invitationRepository.existsByEventIdAndInviteeId(command.getEventId(), inviteeId)) {
            throw EventException.alreadyInvited();
        }

        Invitation invitation = Invitation.builder()
                .eventId(command.getEventId())
                .inviterId(command.getInviterId())
                .inviteeId(inviteeId)
                .status(Invitation.InvitationStatus.PENDING)
                .message(message)
                .sentAt(now)
                .expiresAt(now.plus(INVITATION_TTL))
                .build();

        Invitation savedInvitation;
        try {
            savedInvitation = invitationRepository.save(invitation);
        } catch (DuplicateKeyException sameInviteeTwice) {
            throw EventException.alreadyInvited();
        }

        // $inc: no pisa otros contadores
        eventRepository.incrementStat(command.getEventId(), StatCounter.INVITED, 1);
        
        eventPublisher.publishInvitationSent(command.getEventId(), command.getInviterId(), inviteeId);
        
        return savedInvitation;
    }
    
    /** US4 (FR-012): solo el organizador (antes cualquiera las veía, hallazgo del #204). */
    @Override
    public List<Invitation> getEventInvitations(String eventId, String userId, int page, int size) {
        checkPage(page, size);
        organizedEvent(eventId, userId, EventException::eventNotFound, EventException::onlyOrganizerCanListInvitations);
        Instant now = Instant.now();
        return invitationRepository.findByEventId(eventId, page, Math.max(1, size)).stream()
                .map(invitation -> invitation.asSeenAt(now))
                .toList();
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
