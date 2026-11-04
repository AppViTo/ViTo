package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.model.InviteLink;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService.RespondRSVPCommand;
import com.vito.event.domain.port.inbound.EventService.SendInvitationCommand;
import com.vito.event.domain.port.inbound.EventService.UpdateEventCommand;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.InviteLinkRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T053 (US5, FR-022, FR-023): cancelar sin borrar, idempotente, y lo que deja de admitir. */
@ExtendWith(MockitoExtension.class)
class CancelEventTest {

    private static final String EVENT = "evt_1";
    private static final String ORGANIZER = "usr_org";
    private static final String GUEST = "usr_guest";

    @Mock private EventRepository eventRepository;
    @Mock private RSVPRepository rsvpRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private EventPublisherPort eventPublisher;
    @Mock private QRCodeGenerator qrCodeGenerator;
    @Mock private AttendanceService attendanceService;
    @Mock private InviteLinkRepository inviteLinkRepository;

    @InjectMocks
    private EventServiceImpl eventService;

    private Event event;

    @BeforeEach
    void setUp() {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        event = Event.builder().id(EVENT).organizerId(ORGANIZER).title("Boda").status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(8, ChronoUnit.HOURS)).build())
                .settings(Event.EventSettings.builder().isPublic(true).maxAttendees(100).build())
                .stats(Event.EventStats.builder().totalAccepted(40).build())
                .build();
        lenient().when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        lenient().when(eventRepository.cancel(eq(EVENT), any())).thenReturn(true);
    }

    private EventException rejected(Executable action) {
        return assertThrows(EventException.class, action);
    }

    // ---- cancelar

    @Test
    void cancelsWithAConditionalSetAndPublishes() {
        eventService.cancelEvent(EVENT, ORGANIZER);

        verify(eventRepository).cancel(eq(EVENT), any());
        verify(eventRepository, never()).save(any());
        verify(eventPublisher).publishEventCancelled(EVENT, ORGANIZER);
    }

    @Test
    void neverTouchesInvitationsRsvpsOrLinks() {
        eventService.cancelEvent(EVENT, ORGANIZER);

        verifyNoInteractions(invitationRepository, rsvpRepository, attendanceService);
    }

    @Test
    void cancellingTwiceIsNotAnErrorAndDoesNotNotifyAgain() {
        event.setStatus(Event.EventStatus.CANCELLED);

        assertDoesNotThrow(() -> eventService.cancelEvent(EVENT, ORGANIZER));

        verify(eventRepository, never()).cancel(anyString(), any());
        verify(eventPublisher, never()).publishEventCancelled(anyString(), anyString());
    }

    @Test
    void aCompletedEventIs409() {
        event.setStatus(Event.EventStatus.COMPLETED);

        EventException e = rejected(() -> eventService.cancelEvent(EVENT, ORGANIZER));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("No se puede cancelar un evento que ya terminó", e.getMessage());
    }

    @Test
    void anEndedEventIs409() {
        event.getDetails().setStartDate(Instant.now().minus(2, ChronoUnit.DAYS));
        event.getDetails().setEndDate(Instant.now().minus(1, ChronoUnit.DAYS));

        assertEquals("No se puede cancelar un evento que ya terminó",
                rejected(() -> eventService.cancelEvent(EVENT, ORGANIZER)).getMessage());
        verify(eventRepository, never()).cancel(anyString(), any());
    }

    @Test
    void anEventInProgressCanBeCancelled() {
        event.getDetails().setStartDate(Instant.now().minus(1, ChronoUnit.HOURS));

        eventService.cancelEvent(EVENT, ORGANIZER);

        verify(eventPublisher).publishEventCancelled(EVENT, ORGANIZER);
    }

    @Test
    void anotherUserGets403() {
        EventException e = rejected(() -> eventService.cancelEvent(EVENT, GUEST));

        assertEquals(EventException.Reason.FORBIDDEN, e.getReason());
        assertEquals("Solo el organizador puede cancelar este evento", e.getMessage());
    }

    @Test
    void unknownEventIs404() {
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());

        assertEquals(EventException.Reason.NOT_FOUND, rejected(() -> eventService.cancelEvent("evt_x", ORGANIZER)).getReason());
    }

    @Test
    void aConcurrentCancellationIsStill204WithoutASecondNotice() {
        when(eventRepository.cancel(eq(EVENT), any())).thenReturn(false);
        Event cancelled = event.toBuilder().status(Event.EventStatus.CANCELLED).build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event), Optional.of(cancelled));

        assertDoesNotThrow(() -> eventService.cancelEvent(EVENT, ORGANIZER));
        verify(eventPublisher, never()).publishEventCancelled(anyString(), anyString());
    }

    @Test
    void completedBetweenReadAndWriteIs409() {
        when(eventRepository.cancel(eq(EVENT), any())).thenReturn(false);
        Event completed = event.toBuilder().status(Event.EventStatus.COMPLETED).build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event), Optional.of(completed));

        assertEquals(EventException.Reason.CONFLICT, rejected(() -> eventService.cancelEvent(EVENT, ORGANIZER)).getReason());
    }

    // ---- un evento cancelado deja de admitir respuestas, uniones, invitaciones y ediciones

    @Test
    void afterCancellingRsvpIs409() {
        event.setStatus(Event.EventStatus.CANCELLED);
        lenient().when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.empty());

        EventException e = rejected(() -> eventService.respondToRSVP(RespondRSVPCommand.builder()
                .eventId(EVENT).userId(GUEST).status(RSVP.RSVPStatus.ACCEPTED).build()));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        verifyNoInteractions(attendanceService);
    }

    @Test
    void afterCancellingEditingIs409() {
        event.setStatus(Event.EventStatus.CANCELLED);

        EventException e = rejected(() -> eventService.updateEvent(UpdateEventCommand.builder().eventId(EVENT)
                .userId(ORGANIZER).patch(EventPatch.builder().title(EventPatch.Change.of("Otro título")).build()).build()));

        assertEquals("No se puede editar un evento cancelado o terminado", e.getMessage());
    }

    @Test
    void afterCancellingInvitingIs409() {
        event.setStatus(Event.EventStatus.CANCELLED);

        EventException e = rejected(() -> eventService.sendInvitation(SendInvitationCommand.builder()
                .eventId(EVENT).inviterId(ORGANIZER).inviteeId(GUEST).build()));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("El evento ya no admite invitados", e.getMessage());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void afterCancellingJoiningByLinkIs409() {
        event.setStatus(Event.EventStatus.CANCELLED);
        InviteLink link = InviteLink.builder().id("inl_1").eventId(EVENT).token("k3J9xQ2mV7pL1sT8wY4zBc")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).uses(0).build();
        when(inviteLinkRepository.findByToken(link.getToken())).thenReturn(Optional.of(link));
        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.empty());
        InviteLinkService links = new InviteLinkService(eventRepository, inviteLinkRepository, invitationRepository,
                attendanceService, eventPublisher, Clock.systemUTC());

        EventException e = rejected(() -> links.accept(link.getToken(), GUEST, "Ana"));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        verify(inviteLinkRepository, never()).tryUse(anyString(), any());
    }
}
