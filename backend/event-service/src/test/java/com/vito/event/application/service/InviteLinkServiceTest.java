package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.InviteLink;
import com.vito.event.domain.model.InviteLinkPreview;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.EventRepository.StatCounter;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.InviteLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** T034 (US1): enlaces de invitación, del token a la unión idempotente. */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InviteLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T02:10:00Z");
    private static final String EVENT = "evt_1";
    private static final String ORGANIZER = "usr_org";
    private static final String GUEST = "usr_guest";
    private static final String TOKEN = "k3J9xQ2mV7pL1sT8wY4zBc";

    @Mock private EventRepository eventRepository;
    @Mock private InviteLinkRepository inviteLinkRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private AttendanceService attendanceService;
    @Mock private EventPublisherPort eventPublisher;

    private InviteLinkService service;
    private Event event;
    private InviteLink link;

    @BeforeEach
    void setUp() {
        service = new InviteLinkService(eventRepository, inviteLinkRepository, invitationRepository,
                attendanceService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        Instant start = NOW.plus(30, ChronoUnit.DAYS);
        event = Event.builder().id(EVENT).organizerId(ORGANIZER).organizerDisplayName("Bernardo")
                .title("Cumple de Caro").status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(6, ChronoUnit.HOURS))
                        .location("Cartagena de Indias").address("Calle 1 # 2-3").latitude(10.4).longitude(-75.5)
                        .category("BIRTHDAY").build())
                .settings(Event.EventSettings.builder().isPublic(false).build())
                .createdAt(NOW.minus(10, ChronoUnit.DAYS))
                .build();
        link = InviteLink.builder().id("inl_1").eventId(EVENT).token(TOKEN).createdBy(ORGANIZER)
                .expiresAt(NOW.plus(7, ChronoUnit.DAYS)).uses(0).createdAt(NOW).build();
        lenient().when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        lenient().when(inviteLinkRepository.findByToken(TOKEN)).thenReturn(Optional.of(link));
    }

    private static void assertReason(EventException.Reason reason, Executable call) {
        assertEquals(reason, assertThrows(EventException.class, call::execute).getReason());
    }

    // ---- crear

    private InviteLink create(Integer days, Integer maxUses) {
        when(inviteLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return service.create(EVENT, ORGANIZER, days, maxUses);
    }

    @Test
    void theTokenIs22Base64UrlCharactersAndDifferentEachTime() {
        when(inviteLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = service.create(EVENT, ORGANIZER, null, null).getToken();
            assertTrue(token.matches("^[A-Za-z0-9_-]{22}$"), token);
            tokens.add(token);
        }
        assertEquals(200, tokens.size());
    }

    @Test
    void withoutBodyItLasts7DaysWithoutLimit() {
        InviteLink created = create(null, null);

        assertEquals(NOW.plus(7, ChronoUnit.DAYS), created.getExpiresAt());
        assertNull(created.getMaxUses());
        assertEquals(0, created.getUses());
        assertFalse(created.isRevoked());
        assertEquals(ORGANIZER, created.getCreatedBy());
    }

    @Test
    void theBoundsAreAccepted() {
        assertEquals(NOW.plus(1, ChronoUnit.DAYS), create(1, 1).getExpiresAt());
        assertEquals(NOW.plus(30, ChronoUnit.DAYS), create(30, 1000).getExpiresAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, -1})
    void expiresInDaysOutOfRangeIs400(int days) {
        EventException e = assertThrows(EventException.class, () -> service.create(EVENT, ORGANIZER, days, null));

        assertEquals("expiresInDays debe estar entre 1 y 30", e.getMessage());
        assertEquals("Rango permitido: 1–30", e.getDetails().get("expiresInDays"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1001})
    void maxUsesOutOfRangeIs400(int maxUses) {
        EventException e = assertThrows(EventException.class, () -> service.create(EVENT, ORGANIZER, 7, maxUses));

        assertEquals(EventException.Reason.INVALID, e.getReason());
        assertTrue(e.getDetails().containsKey("maxUses"));
    }

    @Test
    void onlyTheOrganizerCreates() {
        EventException e = assertThrows(EventException.class, () -> service.create(EVENT, GUEST, null, null));

        assertEquals(EventException.Reason.FORBIDDEN, e.getReason());
        assertEquals("Solo el organizador puede crear enlaces", e.getMessage());
    }

    @Test
    void aCancelledEventDoesNotTakeNewLinks() {
        event.setStatus(Event.EventStatus.CANCELLED);

        EventException e = assertThrows(EventException.class, () -> service.create(EVENT, ORGANIZER, null, null));
        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("El evento ya no admite invitados", e.getMessage());
    }

    @Test
    void aTokenCollisionIsRetried() {
        when(inviteLinkRepository.save(any()))
                .thenThrow(new DuplicateKeyException("token"))
                .thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(service.create(EVENT, ORGANIZER, null, null));
        verify(inviteLinkRepository, times(2)).save(any());
    }

    // ---- listar y revocar

    @Test
    void listingIsForTheOrganizerOnlyAndCapsThePage() {
        assertReason(EventException.Reason.FORBIDDEN, () -> service.list(EVENT, GUEST, 0, 20));
        EventException tooBig = assertThrows(EventException.class, () -> service.list(EVENT, ORGANIZER, 0, 101));
        assertEquals("El tamaño de página no puede superar 100", tooBig.getMessage());

        when(inviteLinkRepository.findByEventId(EVENT, NOW, 0, 20)).thenReturn(List.of(link));
        assertEquals(List.of(link), service.list(EVENT, ORGANIZER, 0, 20));
    }

    @Test
    void revokingIsIdempotent() {
        when(inviteLinkRepository.findById("inl_1")).thenReturn(Optional.of(link));
        service.revoke(EVENT, "inl_1", ORGANIZER);
        verify(inviteLinkRepository).revoke("inl_1");

        link.setRevoked(true);
        service.revoke(EVENT, "inl_1", ORGANIZER);
        verify(inviteLinkRepository, times(1)).revoke("inl_1");
    }

    @Test
    void revokingALinkOfAnotherEventIs404() {
        when(inviteLinkRepository.findById("inl_1")).thenReturn(Optional.of(link.toBuilder().eventId("evt_otro").build()));

        EventException e = assertThrows(EventException.class, () -> service.revoke(EVENT, "inl_1", ORGANIZER));
        assertEquals("El evento o el enlace no existe", e.getMessage());
    }

    @Test
    void onlyTheOrganizerRevokes() {
        assertReason(EventException.Reason.FORBIDDEN, () -> service.revoke(EVENT, "inl_1", GUEST));
        verify(inviteLinkRepository, never()).revoke(anyString());
    }

    // ---- vista previa

    @Test
    void thePreviewNeverCarriesTheAddress() {
        InviteLinkPreview preview = service.preview(TOKEN);

        assertEquals("Cumple de Caro", preview.title());
        assertEquals("Cartagena de Indias", preview.location());
        assertEquals("Bernardo", preview.organizerDisplayName());
        assertEquals(link.getExpiresAt(), preview.expiresAt());
        assertFalse(preview.toString().contains("Calle 1"));
    }

    @Test
    void withoutOrganizerNameThePreviewSaysElOrganizador() {
        event.setOrganizerDisplayName(null);

        assertEquals("El organizador", service.preview(TOKEN).organizerDisplayName());
    }

    /** FR-006: el mismo 404 con el mismo texto, sin revelar el caso. */
    @Test
    void expiredRevokedMissingAndMalformedAreTheSame404() {
        InviteLink expired = link.toBuilder().token("a".repeat(22)).expiresAt(NOW.minusSeconds(1)).build();
        InviteLink revoked = link.toBuilder().token("b".repeat(22)).revoked(true).build();
        when(inviteLinkRepository.findByToken(expired.getToken())).thenReturn(Optional.of(expired));
        when(inviteLinkRepository.findByToken(revoked.getToken())).thenReturn(Optional.of(revoked));
        when(inviteLinkRepository.findByToken("c".repeat(22))).thenReturn(Optional.empty());

        for (String token : List.of(expired.getToken(), revoked.getToken(), "c".repeat(22), "corto", "../../etc/passwd!!!!!")) {
            for (Executable call : List.<Executable>of(() -> service.preview(token), () -> service.accept(token, GUEST, "Ana"))) {
                EventException e = assertThrows(EventException.class, call::execute);
                assertEquals(EventException.Reason.NOT_FOUND, e.getReason());
                assertEquals("El enlace no existe o ya no está disponible", e.getMessage());
            }
        }
        verifyNoInteractions(invitationRepository, attendanceService);
    }

    @Test
    void thePreviewOfACancelledEventIsThe404() {
        event.setStatus(Event.EventStatus.CANCELLED);

        assertReason(EventException.Reason.NOT_FOUND, () -> service.preview(TOKEN));
    }

    // ---- unirse

    private void givenNoInvitation() {
        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.empty());
    }

    @Test
    void joiningCreatesAnAcceptedInvitationAndConfirms() {
        givenNoInvitation();
        when(inviteLinkRepository.tryUse("inl_1", NOW)).thenReturn(true);
        when(invitationRepository.save(any())).thenAnswer(inv -> ((Invitation) inv.getArgument(0)).toBuilder().id("inv_1").build());
        when(attendanceService.confirm(EVENT, GUEST, "Ana", null))
                .thenReturn(RSVP.builder().eventId(EVENT).userId(GUEST).status(RSVP.RSVPStatus.ACCEPTED).build());

        InviteLinkService.Joined joined = service.accept(TOKEN, GUEST, "Ana");

        Invitation invitation = joined.invitation();
        assertEquals("inv_1", invitation.getId());
        assertEquals(Invitation.InvitationStatus.ACCEPTED, invitation.getStatus());
        assertEquals("inl_1", invitation.getInviteLinkId());
        assertEquals(ORGANIZER, invitation.getInviterId());
        assertNull(invitation.getExpiresAt());
        assertEquals(NOW, invitation.getRespondedAt());
        verify(eventRepository).incrementStat(EVENT, StatCounter.INVITED, 1);
        verify(eventPublisher).publishRSVPChanged(EVENT, GUEST, "ACCEPTED");
    }

    @Test
    void joiningWithoutSeatLeavesTheRsvpWaitlisted() {
        givenNoInvitation();
        when(inviteLinkRepository.tryUse("inl_1", NOW)).thenReturn(true);
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendanceService.confirm(EVENT, GUEST, "Ana", null))
                .thenReturn(RSVP.builder().status(RSVP.RSVPStatus.WAITLISTED).build());

        InviteLinkService.Joined joined = service.accept(TOKEN, GUEST, "Ana");

        assertEquals(RSVP.RSVPStatus.WAITLISTED, joined.rsvp().orElseThrow().getStatus());
        assertEquals(Invitation.InvitationStatus.ACCEPTED, joined.invitation().getStatus());
    }

    @Test
    void joiningTwiceReturnsTheExistingInvitationWithoutAUse() {
        Invitation existing = Invitation.builder().id("inv_1").eventId(EVENT).inviteeId(GUEST)
                .status(Invitation.InvitationStatus.ACCEPTED).build();
        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.of(existing));

        assertSame(existing, service.accept(TOKEN, GUEST, "Ana").invitation());
        verify(inviteLinkRepository, never()).tryUse(anyString(), any());
        verifyNoInteractions(attendanceService);
    }

    @Test
    void theOrganizerSucceedsWithoutCreatingAnything() {
        InviteLinkService.Joined joined = service.accept(TOKEN, ORGANIZER, "Bernardo");

        assertEquals(EVENT, joined.invitation().getEventId());
        assertNull(joined.invitation().getId());
        verify(inviteLinkRepository, never()).tryUse(anyString(), any());
        verify(invitationRepository, never()).save(any());
        verifyNoInteractions(attendanceService);
    }

    @Test
    void theUseLimitIs409() {
        givenNoInvitation();
        when(inviteLinkRepository.tryUse("inl_1", NOW)).thenReturn(false);

        EventException e = assertThrows(EventException.class, () -> service.accept(TOKEN, GUEST, "Ana"));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("El enlace alcanzó su límite de usos", e.getMessage());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void anEventThatIsNoLongerActiveIs409() {
        givenNoInvitation();
        event.getDetails().setEndDate(NOW.minusSeconds(60));

        EventException e = assertThrows(EventException.class, () -> service.accept(TOKEN, GUEST, "Ana"));
        assertEquals("El evento ya no admite invitados", e.getMessage());
        verify(inviteLinkRepository, never()).tryUse(anyString(), any());
    }

    @Test
    void aRaceOfTheSamePersonGivesTheUseBack() {
        Invitation winner = Invitation.builder().id("inv_1").eventId(EVENT).inviteeId(GUEST).build();
        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(inviteLinkRepository.tryUse("inl_1", NOW)).thenReturn(true);
        when(invitationRepository.save(any())).thenThrow(new DuplicateKeyException("eventId_inviteeId_unique"));

        assertSame(winner, service.accept(TOKEN, GUEST, "Ana").invitation());
        verify(inviteLinkRepository).releaseUse("inl_1");
        verifyNoInteractions(attendanceService);
    }

    /** FR-008: quien se unió conserva su invitación aunque el enlace se revoque después. */
    @Test
    void revokingDoesNotTouchWhoAlreadyJoined() {
        Invitation joined = Invitation.builder().id("inv_1").eventId(EVENT).inviteeId(GUEST)
                .status(Invitation.InvitationStatus.ACCEPTED).inviteLinkId("inl_1").build();
        when(inviteLinkRepository.findById("inl_1")).thenReturn(Optional.of(link));

        service.revoke(EVENT, "inl_1", ORGANIZER);

        verify(invitationRepository, never()).save(any());
        verify(invitationRepository, never()).delete(anyString(), anyString());
        assertEquals(Invitation.InvitationStatus.ACCEPTED, joined.getStatus());
    }

    /** R4: los logs y el evento de dominio llevan el linkId, nunca el token. */
    @Test
    void theTokenNeverReachesTheLogsOrEvents(CapturedOutput output) {
        givenNoInvitation();
        when(inviteLinkRepository.tryUse("inl_1", NOW)).thenReturn(true);
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendanceService.confirm(EVENT, GUEST, "Ana", null))
                .thenReturn(RSVP.builder().status(RSVP.RSVPStatus.ACCEPTED).build());
        when(inviteLinkRepository.save(any())).thenAnswer(inv -> ((InviteLink) inv.getArgument(0)).toBuilder().id("inl_2").build());

        InviteLink created = service.create(EVENT, ORGANIZER, null, null);
        service.preview(TOKEN);
        service.accept(TOKEN, GUEST, "Ana");

        assertTrue(output.getAll().contains("inl_1"), "el log debe identificar el enlace por su id");
        assertFalse(output.getAll().contains(TOKEN));
        assertFalse(output.getAll().contains(created.getToken()));
        ArgumentCaptor<String> args = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publishRSVPChanged(args.capture(), args.capture(), args.capture());
        assertFalse(args.getAllValues().contains(TOKEN));
    }
}
