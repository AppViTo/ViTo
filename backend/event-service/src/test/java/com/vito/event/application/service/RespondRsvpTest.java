package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService.RespondRSVPCommand;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** T028 (US3): respondRsvp como upsert, con el acceso de R3 y la invitación de FR-017. */
@ExtendWith(MockitoExtension.class)
class RespondRsvpTest {

    private static final String EVENT = "evt_1";
    private static final String GUEST = "usr_guest";
    private static final String ORGANIZER = "usr_org";

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
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        event = Event.builder()
                .id(EVENT)
                .organizerId(ORGANIZER)
                .status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(4, ChronoUnit.HOURS)).build())
                .settings(Event.EventSettings.builder().isPublic(true).maxAttendees(20).build())
                .stats(Event.EventStats.builder().build())
                .build();
        lenient().when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        lenient().when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.empty());
    }

    private static RespondRSVPCommand answer(RSVP.RSVPStatus status) {
        return RespondRSVPCommand.builder().eventId(EVENT).userId(GUEST).displayName("Ana").status(status).build();
    }

    private static RSVP rsvp(RSVP.RSVPStatus status) {
        return RSVP.builder().eventId(EVENT).userId(GUEST).status(status).respondedAt(Instant.now()).build();
    }

    private void givenConfirmReturns(RSVP.RSVPStatus status) {
        when(attendanceService.confirm(EVENT, GUEST, "Ana", null)).thenReturn(rsvp(status));
    }

    private void makePrivate() {
        event.getSettings().setPublic(false);
    }

    private static void assertConflict(EventException e) {
        assertEquals(EventException.Reason.CONFLICT, e.getReason());
    }

    // ---- upsert (FR-019)

    @Test
    void acceptingReservesThroughAttendanceAndPublishes() {
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        RSVP result = eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));

        assertEquals(RSVP.RSVPStatus.ACCEPTED, result.getStatus());
        verify(eventPublisher).publishRSVPChanged(EVENT, GUEST, "ACCEPTED");
    }

    @Test
    void withoutSeatTheAnswerIsWaitlisted() {
        givenConfirmReturns(RSVP.RSVPStatus.WAITLISTED);

        assertEquals(RSVP.RSVPStatus.WAITLISTED, eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED)).getStatus());
        verify(eventPublisher).publishRSVPChanged(EVENT, GUEST, "WAITLISTED");
    }

    @Test
    void theSameAnswerTwiceGoesThroughTheIdempotentUpsertWithoutError() {
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));
        eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));

        verify(attendanceService, times(2)).confirm(EVENT, GUEST, "Ana", null);
        verify(rsvpRepository, never()).save(any());
    }

    @Test
    void changingTheAnswerIsNotAnError() {
        when(attendanceService.release(EVENT, GUEST, RSVP.RSVPStatus.DECLINED, "Ana", null))
                .thenReturn(rsvp(RSVP.RSVPStatus.DECLINED));

        assertEquals(RSVP.RSVPStatus.DECLINED, eventService.respondToRSVP(answer(RSVP.RSVPStatus.DECLINED)).getStatus());
    }

    @Test
    void doesNotGenerateACheckInQr() {
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));

        verifyNoInteractions(qrCodeGenerator);
    }

    // ---- entrada (400)

    @ParameterizedTest
    @EnumSource(value = RSVP.RSVPStatus.class, names = {"PENDING", "WAITLISTED"})
    void pendingAndWaitlistedAreNotAnswers(RSVP.RSVPStatus status) {
        EventException e = assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(status)));

        assertEquals(EventException.Reason.INVALID, e.getReason());
        assertEquals("El status debe ser ACCEPTED, DECLINED o MAYBE", e.getMessage());
        verifyNoInteractions(attendanceService);
    }

    @Test
    void aMessageOver280CharactersIsRejected() {
        RespondRSVPCommand command = answer(RSVP.RSVPStatus.ACCEPTED);
        command.setMessage("x".repeat(281));

        EventException e = assertThrows(EventException.class, () -> eventService.respondToRSVP(command));

        assertEquals(EventException.Reason.INVALID, e.getReason());
        assertTrue(e.getDetails().containsKey("message"));
    }

    @Test
    void aMessageOf280CharactersIsAccepted() {
        String message = "x".repeat(280);
        when(attendanceService.confirm(EVENT, GUEST, "Ana", message)).thenReturn(rsvp(RSVP.RSVPStatus.ACCEPTED));
        RespondRSVPCommand command = answer(RSVP.RSVPStatus.ACCEPTED);
        command.setMessage(message);

        assertNotNull(eventService.respondToRSVP(command));
    }

    // ---- acceso a privados (R3, FR-018)

    @Test
    void aMissingEventIs404() {
        when(eventRepository.findById(EVENT)).thenReturn(Optional.empty());

        EventException e = assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED)));
        assertEquals(EventException.Reason.NOT_FOUND, e.getReason());
    }

    @Test
    void aPrivateEventWithoutInvitationIsTheSame404AsAMissingOne() {
        makePrivate();
        when(rsvpRepository.existsByEventIdAndUserId(EVENT, GUEST)).thenReturn(false);

        EventException e = assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED)));

        EventException missing = EventException.eventNotFound();
        assertEquals(missing.getReason(), e.getReason());
        assertEquals(missing.getMessage(), e.getMessage());
        verifyNoInteractions(attendanceService);
    }

    @Test
    void aPrivateEventWithAValidInvitationCanBeAnswered() {
        makePrivate();
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        assertNotNull(eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED)));
    }

    @Test
    void anExpiredInvitationWithoutRsvpLosesAccess() {
        makePrivate();
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().minus(1, ChronoUnit.DAYS));
        when(rsvpRepository.existsByEventIdAndUserId(EVENT, GUEST)).thenReturn(false);

        EventException e = assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(RSVP.RSVPStatus.MAYBE)));
        assertEquals(EventException.Reason.NOT_FOUND, e.getReason());
    }

    @Test
    void whoAlreadyAnsweredKeepsAccessAfterTheInvitationExpires() {
        makePrivate();
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().minus(1, ChronoUnit.DAYS));
        when(rsvpRepository.existsByEventIdAndUserId(EVENT, GUEST)).thenReturn(true);
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        assertEquals(RSVP.RSVPStatus.ACCEPTED, eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED)).getStatus());
    }

    @Test
    void anAcceptedInvitationKeepsAccessEvenIfItsDateHasPassed() {
        makePrivate();
        givenInvitation(Invitation.InvitationStatus.ACCEPTED, Instant.now().minus(1, ChronoUnit.DAYS));
        when(attendanceService.release(EVENT, GUEST, RSVP.RSVPStatus.MAYBE, "Ana", null))
                .thenReturn(rsvp(RSVP.RSVPStatus.MAYBE));

        assertNotNull(eventService.respondToRSVP(answer(RSVP.RSVPStatus.MAYBE)));
    }

    // ---- 409

    @Test
    void theOrganizerCannotAnswerTheirOwnEvent() {
        RespondRSVPCommand command = answer(RSVP.RSVPStatus.ACCEPTED);
        command.setUserId(ORGANIZER);

        EventException e = assertThrows(EventException.class, () -> eventService.respondToRSVP(command));

        assertConflict(e);
        assertEquals("No puedes responder RSVP a tu propio evento", e.getMessage());
    }

    @Test
    void aCancelledEventIs409() {
        event.setStatus(Event.EventStatus.CANCELLED);

        assertConflict(assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED))));
        verifyNoInteractions(attendanceService);
    }

    @Test
    void aCompletedEventIs409() {
        event.setStatus(Event.EventStatus.COMPLETED);

        assertConflict(assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(RSVP.RSVPStatus.DECLINED))));
    }

    @Test
    void anEventWhoseEndDateHasPassedIs409() {
        event.getDetails().setEndDate(Instant.now().minus(1, ChronoUnit.HOURS));

        assertConflict(assertThrows(EventException.class, () -> eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED))));
    }

    // ---- la invitación sigue a la respuesta (FR-017)

    private Invitation givenInvitation(Invitation.InvitationStatus status, Instant expiresAt) {
        Invitation invitation = Invitation.builder().id("inv_1").eventId(EVENT).inviteeId(GUEST)
                .status(status).expiresAt(expiresAt).build();
        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.of(invitation));
        return invitation;
    }

    private Invitation savedInvitation() {
        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void acceptingAcceptsThePendingInvitation() {
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));

        Invitation saved = savedInvitation();
        assertEquals(Invitation.InvitationStatus.ACCEPTED, saved.getStatus());
        assertNotNull(saved.getRespondedAt());
    }

    @Test
    void beingWaitlistedAlsoAcceptsTheInvitation() {
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        givenConfirmReturns(RSVP.RSVPStatus.WAITLISTED);

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));

        assertEquals(Invitation.InvitationStatus.ACCEPTED, savedInvitation().getStatus());
    }

    @Test
    void decliningDeclinesTheInvitation() {
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        when(attendanceService.release(EVENT, GUEST, RSVP.RSVPStatus.DECLINED, "Ana", null))
                .thenReturn(rsvp(RSVP.RSVPStatus.DECLINED));

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.DECLINED));

        assertEquals(Invitation.InvitationStatus.DECLINED, savedInvitation().getStatus());
    }

    @Test
    void maybeLeavesThePendingInvitationUntouched() {
        givenInvitation(Invitation.InvitationStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        when(attendanceService.release(EVENT, GUEST, RSVP.RSVPStatus.MAYBE, "Ana", null))
                .thenReturn(rsvp(RSVP.RSVPStatus.MAYBE));

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.MAYBE));

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void maybeAfterAcceptingPutsTheInvitationBackToPending() {
        givenInvitation(Invitation.InvitationStatus.ACCEPTED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(attendanceService.release(EVENT, GUEST, RSVP.RSVPStatus.MAYBE, "Ana", null))
                .thenReturn(rsvp(RSVP.RSVPStatus.MAYBE));

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.MAYBE));

        Invitation saved = savedInvitation();
        assertEquals(Invitation.InvitationStatus.PENDING, saved.getStatus());
        assertNull(saved.getRespondedAt());
    }

    @Test
    void withoutInvitationNothingIsSavedThere() {
        givenConfirmReturns(RSVP.RSVPStatus.ACCEPTED);

        eventService.respondToRSVP(answer(RSVP.RSVPStatus.ACCEPTED));

        verify(invitationRepository, never()).save(any());
        verify(eventPublisher, never()).publishInvitationSent(anyString(), anyString(), anyString());
    }
}
