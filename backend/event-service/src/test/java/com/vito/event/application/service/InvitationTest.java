package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService.SendInvitationCommand;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.EventRepository.StatCounter;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T050 (US4, FR-010 a FR-012) y T056 (US6, FR-024): invitaciones por la app y asistentes, solo del organizador. */
@ExtendWith(MockitoExtension.class)
class InvitationTest {

    private static final String EVENT = "evt_1";
    private static final String ORGANIZER = "usr_org";
    private static final String GUEST = "usr_guest";

    @Mock private EventRepository eventRepository;
    @Mock private RSVPRepository rsvpRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private EventPublisherPort eventPublisher;
    @Mock private QRCodeGenerator qrCodeGenerator;
    @Mock private AttendanceService attendanceService;

    @InjectMocks
    private EventServiceImpl eventService;

    private Event event;

    @BeforeEach
    void setUp() {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        event = Event.builder().id(EVENT).organizerId(ORGANIZER).title("Boda").status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(8, ChronoUnit.HOURS)).build())
                .settings(Event.EventSettings.builder().isPublic(false).build())
                .build();
        lenient().when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        lenient().when(invitationRepository.save(any())).thenAnswer(call ->
                ((Invitation) call.getArgument(0)).toBuilder().id("inv_1").build());
    }

    private static SendInvitationCommand.SendInvitationCommandBuilder invite(String inviter, String invitee) {
        return SendInvitationCommand.builder().eventId(EVENT).inviterId(inviter).inviteeId(invitee);
    }

    private EventException rejected(Executable action) {
        return assertThrows(EventException.class, action);
    }

    private void assertNothingWritten() {
        verify(invitationRepository, never()).save(any());
        verify(eventRepository, never()).incrementStat(anyString(), any(), anyInt());
        verify(eventPublisher, never()).publishInvitationSent(anyString(), anyString(), anyString());
    }

    // ---- sendInvitation

    @Test
    void invitingCreatesAPendingInvitationThatExpiresIn7Days() {
        Instant before = Instant.now();

        Invitation sent = eventService.sendInvitation(invite(ORGANIZER, GUEST).message("¡Te espero!").build());

        assertEquals("inv_1", sent.getId());
        assertEquals(Invitation.InvitationStatus.PENDING, sent.getStatus());
        assertEquals("¡Te espero!", sent.getMessage());
        assertNull(sent.getInviteLinkId());
        assertNull(sent.getRespondedAt());
        assertFalse(sent.getSentAt().isBefore(before));
        assertEquals(Duration.ofDays(7), Duration.between(sent.getSentAt(), sent.getExpiresAt()));
    }

    @Test
    void invitingIncrementsTotalInvitedWithIncAndPublishes() {
        eventService.sendInvitation(invite(ORGANIZER, GUEST).build());

        verify(eventRepository).incrementStat(EVENT, StatCounter.INVITED, 1);
        verify(eventRepository, never()).save(any());
        verify(eventPublisher).publishInvitationSent(EVENT, ORGANIZER, GUEST);
    }

    @Test
    void theInviteeIdIsTrimmed() {
        eventService.sendInvitation(invite(ORGANIZER, "  " + GUEST + " ").build());

        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(saved.capture());
        assertEquals(GUEST, saved.getValue().getInviteeId());
        verify(invitationRepository).existsByEventIdAndInviteeId(EVENT, GUEST);
    }

    @Test
    void anotherUserCannotInvite403() {
        EventException e = rejected(() -> eventService.sendInvitation(invite(GUEST, "usr_otro").build()));

        assertEquals(EventException.Reason.FORBIDDEN, e.getReason());
        assertEquals("Solo el organizador puede invitar a este evento", e.getMessage());
        assertNothingWritten();
    }

    @Test
    void unknownEventIs404WithTheContractText() {
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());

        EventException e = rejected(() -> eventService.sendInvitation(
                SendInvitationCommand.builder().eventId("evt_x").inviterId(ORGANIZER).inviteeId(GUEST).build()));

        assertEquals(EventException.Reason.NOT_FOUND, e.getReason());
        assertEquals("El evento o el usuario no existe", e.getMessage());
    }

    @Test
    void aMissingOrBlankInviteeIs400() {
        for (String invitee : new String[] {null, "", "   "}) {
            EventException e = rejected(() -> eventService.sendInvitation(invite(ORGANIZER, invitee).build()));
            assertEquals(EventException.Reason.INVALID, e.getReason());
            assertEquals("El campo inviteeId es obligatorio", e.getMessage());
        }
        assertNothingWritten();
    }

    @Test
    void theOrganizerCannotInviteThemselves() {
        EventException e = rejected(() -> eventService.sendInvitation(invite(ORGANIZER, ORGANIZER).build()));

        assertEquals(EventException.Reason.INVALID, e.getReason());
        assertEquals("Debe ser otra persona", e.getDetails().get("inviteeId"));
        assertNothingWritten();
    }

    @Test
    void theMessageAllows280CharactersCountingEmojiAsOne() {
        String emoji280 = "🎉".repeat(280); // 560 unidades UTF-16, 280 caracteres

        assertDoesNotThrow(() -> eventService.sendInvitation(invite(ORGANIZER, GUEST).message(emoji280).build()));

        EventException e = rejected(() -> eventService.sendInvitation(
                invite(ORGANIZER, "usr_otro").message("a".repeat(281)).build()));
        assertEquals(EventException.Reason.INVALID, e.getReason());
        assertEquals("Máximo 280 caracteres", e.getDetails().get("message"));
    }

    @Test
    void invitingSomeoneAlreadyInvitedIs409() {
        when(invitationRepository.existsByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(true);

        EventException e = rejected(() -> eventService.sendInvitation(invite(ORGANIZER, GUEST).build()));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("El usuario ya está invitado a este evento", e.getMessage());
        assertNothingWritten();
    }

    @Test
    void losingTheRaceToTheUniqueIndexIs409WithoutCounting() {
        doThrow(new DuplicateKeyException("eventId_inviteeId_unique")).when(invitationRepository).save(any());

        EventException e = rejected(() -> eventService.sendInvitation(invite(ORGANIZER, GUEST).build()));

        assertEquals("El usuario ya está invitado a este evento", e.getMessage());
        verify(eventRepository, never()).incrementStat(anyString(), any(), anyInt());
        verify(eventPublisher, never()).publishInvitationSent(anyString(), anyString(), anyString());
    }

    @Test
    void anEndedEventDoesNotAcceptInvitations() {
        event.getDetails().setStartDate(Instant.now().minus(2, ChronoUnit.DAYS));
        event.getDetails().setEndDate(Instant.now().minus(1, ChronoUnit.DAYS));

        EventException e = rejected(() -> eventService.sendInvitation(invite(ORGANIZER, GUEST).build()));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertNothingWritten();
    }

    // ---- listEventInvitations

    @Test
    void theOrganizerListsInvitationsWithExpiredComputedOnRead() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        Invitation overdue = Invitation.builder().id("inv_a").status(Invitation.InvitationStatus.PENDING).expiresAt(past).build();
        Invitation pending = Invitation.builder().id("inv_b").status(Invitation.InvitationStatus.PENDING).expiresAt(future).build();
        Invitation accepted = Invitation.builder().id("inv_c").status(Invitation.InvitationStatus.ACCEPTED).expiresAt(past).build();
        Invitation byLink = Invitation.builder().id("inv_d").status(Invitation.InvitationStatus.ACCEPTED)
                .inviteLinkId("inl_1").build();
        when(invitationRepository.findByEventId(EVENT, 0, 20)).thenReturn(List.of(overdue, pending, accepted, byLink));

        List<Invitation.InvitationStatus> statuses = eventService.getEventInvitations(EVENT, ORGANIZER, 0, 20).stream()
                .map(Invitation::getStatus).toList();

        assertEquals(List.of(Invitation.InvitationStatus.EXPIRED, Invitation.InvitationStatus.PENDING,
                Invitation.InvitationStatus.ACCEPTED, Invitation.InvitationStatus.ACCEPTED), statuses);
        assertEquals(Invitation.InvitationStatus.PENDING, overdue.getStatus(), "no cambia lo guardado");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void anInvitedGuestCannotListInvitations403() {
        EventException e = rejected(() -> eventService.getEventInvitations(EVENT, GUEST, 0, 20));

        assertEquals(EventException.Reason.FORBIDDEN, e.getReason());
        assertEquals("Solo el organizador puede ver las invitaciones", e.getMessage());
        verify(invitationRepository, never()).findByEventId(anyString(), anyInt(), anyInt());
    }

    @Test
    void listingInvitationsOfAnUnknownEventIs404() {
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());

        assertEquals("El evento no existe", rejected(() -> eventService.getEventInvitations("evt_x", ORGANIZER, 0, 20)).getMessage());
    }

    @Test
    void invitationPagingIsValidated() {
        assertEquals("El tamaño de página no puede superar 100",
                rejected(() -> eventService.getEventInvitations(EVENT, ORGANIZER, 0, 101)).getMessage());
        assertEquals(EventException.Reason.INVALID,
                rejected(() -> eventService.getEventInvitations(EVENT, ORGANIZER, -1, 20)).getReason());

        eventService.getEventInvitations(EVENT, ORGANIZER, 0, 0);
        verify(invitationRepository).findByEventId(EVENT, 0, 1);
    }

    // ---- listEventAttendees (US6)

    @Test
    void theOrganizerListsAttendeesWithTheStatusFilter() {
        RSVP rsvp = RSVP.builder().userId(GUEST).status(RSVP.RSVPStatus.ACCEPTED).build();
        when(rsvpRepository.findByEventId(EVENT, RSVP.RSVPStatus.ACCEPTED, 2, 10)).thenReturn(List.of(rsvp));

        assertEquals(List.of(rsvp), eventService.getEventAttendees(EVENT, ORGANIZER, RSVP.RSVPStatus.ACCEPTED, 2, 10));

        eventService.getEventAttendees(EVENT, ORGANIZER, null, 0, 20);
        verify(rsvpRepository).findByEventId(EVENT, null, 0, 20);
    }

    @Test
    void aConfirmedGuestStillGets403OnAttendees() {
        lenient().when(rsvpRepository.findByEventIdAndUserId(EVENT, GUEST))
                .thenReturn(Optional.of(RSVP.builder().userId(GUEST).status(RSVP.RSVPStatus.ACCEPTED).build()));

        EventException e = rejected(() -> eventService.getEventAttendees(EVENT, GUEST, null, 0, 20));

        assertEquals(EventException.Reason.FORBIDDEN, e.getReason());
        assertEquals("Solo el organizador puede ver la lista de asistentes", e.getMessage());
        verify(rsvpRepository, never()).findByEventId(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void attendeesOfAnUnknownEventIs404AndPagingIsValidated() {
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());

        assertEquals(EventException.Reason.NOT_FOUND,
                rejected(() -> eventService.getEventAttendees("evt_x", ORGANIZER, null, 0, 20)).getReason());
        assertEquals(EventException.Reason.INVALID,
                rejected(() -> eventService.getEventAttendees(EVENT, ORGANIZER, null, 0, 101)).getReason());
        verify(rsvpRepository, never()).findByEventId(eq(EVENT), any(), anyInt(), anyInt());
    }
}
