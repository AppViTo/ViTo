package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.model.EventPatch.Change;
import com.vito.event.domain.port.inbound.EventService.UpdateEventCommand;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T045 (US2, FR-020, FR-021, FR-023): edición parcial del evento. */
@ExtendWith(MockitoExtension.class)
class UpdateEventTest {

    private static final String EVENT = "evt_1";
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
    private Instant start;

    @BeforeEach
    void setUp() {
        start = Instant.now().plus(10, ChronoUnit.DAYS);
        event = Event.builder()
                .id(EVENT).organizerId(ORGANIZER).organizerDisplayName("Alex").title("Boda Andrea & Luis")
                .status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(8, ChronoUnit.HOURS))
                        .location("Hacienda El Retiro").category("WEDDING").build())
                .settings(Event.EventSettings.builder().isPublic(true).maxAttendees(150).build())
                .stats(Event.EventStats.builder().totalAccepted(96).build())
                .build();
        lenient().when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        lenient().when(eventRepository.applyPatch(eq(EVENT), any(), any(), any())).thenReturn(Optional.of(event));
    }

    private Event update(EventPatch patch) {
        return update(ORGANIZER, patch);
    }

    private Event update(String userId, EventPatch patch) {
        return eventService.updateEvent(UpdateEventCommand.builder()
                .eventId(EVENT).userId(userId).organizerDisplayName("Alex Villa").patch(patch).build());
    }

    private EventException rejected(EventPatch patch) {
        return assertThrows(EventException.class, () -> update(patch));
    }

    private static EventPatch title(String title) {
        return EventPatch.builder().title(Change.of(title)).build();
    }

    // ---- solo cambian los campos enviados

    @Test
    void onlyTheSentFieldsGoToTheRepository() {
        EventPatch patch = EventPatch.builder().title(Change.of("Boda — ceremonia y fiesta"))
                .maxAttendees(Change.of(160)).build();

        Event result = update(patch);

        verify(eventRepository).applyPatch(eq(EVENT), eq(patch), eq("Alex Villa"), any());
        verify(eventRepository, never()).save(any());
        verify(eventPublisher).publishEventUpdated(EVENT, ORGANIZER);
        assertSame(event, result);
    }

    @Test
    void theRepositoryReceivesTheEditTimeAsNow() {
        Instant before = Instant.now();
        update(title("Otro título"));

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(eventRepository).applyPatch(eq(EVENT), any(), any(), now.capture());
        assertFalse(now.getValue().isBefore(before));
    }

    // ---- validaciones (las de createEvent y las de details/settings)

    @Test
    void titleFollowsTheCreateRules() {
        assertEquals("El campo title no es válido", rejected(title("ab")).getMessage());
        assertEquals("Es obligatorio", rejected(title(null)).getDetails().get("title"));
        assertEquals(EventException.Reason.INVALID, rejected(title("   ")).getReason());
        assertEquals(EventException.Reason.INVALID, rejected(title("x".repeat(101))).getReason());
        verify(eventRepository, never()).applyPatch(any(), any(), any(), any());
    }

    @Test
    void lengthsCategoryAndCoordinatesAreValidated() {
        assertTrue(rejected(EventPatch.builder().description(Change.of("x".repeat(2001))).build())
                .getDetails().containsKey("description"));
        assertTrue(rejected(EventPatch.builder().location(Change.of("x".repeat(301))).build())
                .getDetails().containsKey("details.location"));
        assertTrue(rejected(EventPatch.builder().category(Change.of("CONCERT")).build())
                .getDetails().containsKey("details.category"));
        assertTrue(rejected(EventPatch.builder().latitude(Change.of(91.0)).build())
                .getDetails().containsKey("details.latitude"));
        assertTrue(rejected(EventPatch.builder().maxAttendees(Change.of(-1)).build())
                .getDetails().containsKey("settings.maxAttendees"));
        assertTrue(rejected(EventPatch.builder().isPublic(Change.of(null)).build())
                .getDetails().containsKey("settings.isPublic"));
    }

    @Test
    void descriptionCanBeErased() {
        update(EventPatch.builder().description(Change.of(null)).build());

        verify(eventRepository).applyPatch(eq(EVENT), any(), any(), any());
    }

    @Test
    void endDateMustFollowStartDateAfterThePatch() {
        // Solo endDate: se compara con el startDate guardado
        EventException onlyEnd = rejected(EventPatch.builder().endDate(Change.of(start.minus(1, ChronoUnit.HOURS))).build());
        assertTrue(onlyEnd.getDetails().containsKey("details.endDate"));
        // Solo startDate después del endDate guardado
        EventException onlyStart = rejected(EventPatch.builder().startDate(Change.of(start.plus(9, ChronoUnit.HOURS))).build());
        assertTrue(onlyStart.getDetails().containsKey("details.startDate"));
        // Iguales tampoco
        assertEquals(EventException.Reason.INVALID, rejected(EventPatch.builder()
                .startDate(Change.of(start)).endDate(Change.of(start)).build()).getReason());
        // Las dos juntas y en orden: válido
        update(EventPatch.builder().startDate(Change.of(start.plus(1, ChronoUnit.DAYS)))
                .endDate(Change.of(start.plus(2, ChronoUnit.DAYS))).build());
    }

    @Test
    void anEmptyPatchIs400() {
        assertEquals(EventException.Reason.INVALID, rejected(EventPatch.builder().build()).getReason());
    }

    // ---- permisos y estado

    @Test
    void anotherUserGets403() {
        EventException e = assertThrows(EventException.class, () -> update("usr_otro", title("Otro título")));

        assertEquals(EventException.Reason.FORBIDDEN, e.getReason());
        assertEquals("Solo el organizador puede editar este evento", e.getMessage());
    }

    @Test
    void unknownEventIs404() {
        when(eventRepository.findById(EVENT)).thenReturn(Optional.empty());

        assertEquals(EventException.Reason.NOT_FOUND, rejected(title("Otro título")).getReason());
    }

    @ParameterizedTest
    @EnumSource(value = Event.EventStatus.class, names = {"CANCELLED", "COMPLETED"})
    void cancelledOrCompletedIs409(Event.EventStatus status) {
        event.setStatus(status);

        EventException e = rejected(title("Otro título"));

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("No se puede editar un evento cancelado o terminado", e.getMessage());
    }

    @Test
    void anEndedEventIs409() {
        event.getDetails().setStartDate(Instant.now().minus(2, ChronoUnit.DAYS));
        event.getDetails().setEndDate(Instant.now().minus(1, ChronoUnit.DAYS));

        assertEquals("No se puede editar un evento cancelado o terminado", rejected(title("Otro título")).getMessage());
    }

    @Test
    void aDraftCanBeEdited() {
        event.setStatus(Event.EventStatus.DRAFT);

        update(title("Otro título"));

        verify(eventRepository).applyPatch(eq(EVENT), any(), any(), any());
    }

    // ---- cupo (FR-021)

    @Test
    void capacityBelowAcceptedIs409WithTheCount() {
        EventException e = rejected(EventPatch.builder().maxAttendees(Change.of(95)).build());

        assertEquals(EventException.Reason.CONFLICT, e.getReason());
        assertEquals("El cupo no puede ser menor que los 96 confirmados", e.getMessage());
        verify(eventRepository, never()).applyPatch(any(), any(), any(), any());
    }

    @Test
    void capacityEqualToAcceptedOrUnlimitedIsAllowed() {
        update(EventPatch.builder().maxAttendees(Change.of(96)).build());
        update(EventPatch.builder().maxAttendees(Change.of(0)).build());

        verify(eventRepository, times(2)).applyPatch(eq(EVENT), any(), any(), any());
    }

    @Test
    void aConfirmationBetweenReadAndWriteIs409WithTheNewCount() {
        // La escritura condicional no aplicó: alguien confirmó mientras tanto
        when(eventRepository.applyPatch(eq(EVENT), any(), any(), any())).thenReturn(Optional.empty());
        Event latest = event.toBuilder().stats(Event.EventStats.builder().totalAccepted(97).build()).build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event), Optional.of(latest));

        EventException e = rejected(EventPatch.builder().maxAttendees(Change.of(96)).build());

        assertEquals("El cupo no puede ser menor que los 97 confirmados", e.getMessage());
        verify(eventPublisher, never()).publishEventUpdated(anyString(), anyString());
    }

    @Test
    void aCancellationBetweenReadAndWriteIs409State() {
        when(eventRepository.applyPatch(eq(EVENT), any(), any(), any())).thenReturn(Optional.empty());
        Event cancelled = event.toBuilder().status(Event.EventStatus.CANCELLED).build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event), Optional.of(cancelled));

        assertEquals("No se puede editar un evento cancelado o terminado", rejected(title("Otro título")).getMessage());
    }

    @Test
    void raisingTheCapacityPromotesTheWaitlist() {
        when(attendanceService.fillFreedSeats(EVENT)).thenReturn(3);

        update(EventPatch.builder().maxAttendees(Change.of(200)).build());

        verify(attendanceService).fillFreedSeats(EVENT);
        // Se relee el evento para devolver los contadores con los promovidos
        verify(eventRepository, times(2)).findById(EVENT);
    }

    @Test
    void removingTheLimitPromotesTheWaitlist() {
        update(EventPatch.builder().maxAttendees(Change.of(0)).build());

        verify(attendanceService).fillFreedSeats(EVENT);
    }

    @Test
    void loweringOrKeepingTheCapacityDoesNotPromote() {
        update(EventPatch.builder().maxAttendees(Change.of(120)).build());
        update(EventPatch.builder().maxAttendees(Change.of(150)).build());
        update(title("Otro título"));

        verify(attendanceService, never()).fillFreedSeats(anyString());
    }

    @Test
    void fromUnlimitedNothingIsWaitlistedSoNothingIsPromoted() {
        event.getSettings().setMaxAttendees(0);

        update(EventPatch.builder().maxAttendees(Change.of(200)).build());

        verify(attendanceService, never()).fillFreedSeats(anyString());
    }
}
