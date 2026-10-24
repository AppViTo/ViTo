package com.vito.event.application.service;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.inbound.EventService.*;
import com.vito.event.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RSVPRepository rsvpRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private QRCodeGenerator qrCodeGenerator;

    @InjectMocks
    private EventServiceImpl eventService;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id("event123")
                .organizerId("organizer123")
                .title("Test Event")
                .description("Test Description")
                .details(Event.EventDetails.builder()
                        .startDate(LocalDateTime.now().plusDays(7))
                        .location("Test Location")
                        .build())
                .settings(Event.EventSettings.builder()
                        .isPublic(true)
                        .maxAttendees(100)
                        .build())
                .stats(Event.EventStats.builder().build())
                .status(Event.EventStatus.PUBLISHED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createEvent_Success() {
        // Arrange
        CreateEventCommand command = CreateEventCommand.builder()
                .organizerId("organizer123")
                .title("Test Event")
                .description("Test Description")
                .build();

        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // Act
        Event result = eventService.createEvent(command);

        // Assert
        assertNotNull(result);
        assertEquals("Test Event", result.getTitle());
        assertEquals("organizer123", result.getOrganizerId());

        verify(eventRepository).save(any(Event.class));
        verify(eventPublisher).publishEventCreated("event123", "organizer123");
    }

    @Test
    void updateEvent_Success() {
        // Arrange
        UpdateEventCommand command = UpdateEventCommand.builder()
                .eventId("event123")
                .userId("organizer123")
                .title("Updated Title")
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // Act
        Event result = eventService.updateEvent(command);

        // Assert
        assertNotNull(result);

        verify(eventRepository).findById("event123");
        verify(eventRepository).save(any(Event.class));
        verify(eventPublisher).publishEventUpdated("event123", "organizer123");
    }

    @Test
    void updateEvent_NotAuthorized_ThrowsException() {
        // Arrange
        UpdateEventCommand command = UpdateEventCommand.builder()
                .eventId("event123")
                .userId("user456")
                .title("Updated Title")
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));

        // Act & Assert
        EventException exception = assertThrows(
                EventException.class,
                () -> eventService.updateEvent(command)
        );
        assertEquals("No tienes permiso para realizar esta acción", exception.getMessage());
    }

    @Test
    void cancelEvent_Success() {
        // Arrange
        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // Act
        eventService.cancelEvent("event123", "organizer123");

        // Assert
        verify(eventRepository).findById("event123");
        verify(eventRepository).save(any(Event.class));
        verify(eventPublisher).publishEventCancelled("event123", "organizer123");
    }

    @Test
    void respondToRSVP_Success() {
        // Arrange
        RespondRSVPCommand command = RespondRSVPCommand.builder()
                .eventId("event123")
                .userId("user456")
                .status(RSVP.RSVPStatus.ACCEPTED)
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(rsvpRepository.existsByEventIdAndUserId("event123", "user456")).thenReturn(false);
        when(rsvpRepository.countByEventIdAndStatus("event123", RSVP.RSVPStatus.ACCEPTED)).thenReturn(5L);
        when(qrCodeGenerator.generateCheckInQR("event123", "user456")).thenReturn("qr_code_123");
        when(rsvpRepository.save(any(RSVP.class))).thenReturn(RSVP.builder().build());

        // Act
        RSVP result = eventService.respondToRSVP(command);

        // Assert
        assertNotNull(result);

        verify(eventRepository, atLeastOnce()).findById("event123");
        verify(rsvpRepository).existsByEventIdAndUserId("event123", "user456");
        verify(rsvpRepository).save(any(RSVP.class));
        verify(eventPublisher).publishRSVPChanged("event123", "user456", "ACCEPTED");
    }

    @Test
    void respondToRSVP_CannotRSVPToOwnEvent_ThrowsException() {
        // Arrange
        RespondRSVPCommand command = RespondRSVPCommand.builder()
                .eventId("event123")
                .userId("organizer123")
                .status(RSVP.RSVPStatus.ACCEPTED)
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));

        // Act & Assert
        EventException exception = assertThrows(
                EventException.class,
                () -> eventService.respondToRSVP(command)
        );
        assertEquals("No puedes responder a tu propio evento", exception.getMessage());
    }

    @Test
    void respondToRSVP_AlreadyRSVP_ThrowsException() {
        // Arrange
        RespondRSVPCommand command = RespondRSVPCommand.builder()
                .eventId("event123")
                .userId("user456")
                .status(RSVP.RSVPStatus.ACCEPTED)
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(rsvpRepository.existsByEventIdAndUserId("event123", "user456")).thenReturn(true);

        // Act & Assert
        EventException exception = assertThrows(
                EventException.class,
                () -> eventService.respondToRSVP(command)
        );
        assertEquals("Ya respondiste a este evento", exception.getMessage());
    }

    @Test
    void sendInvitation_Success() {
        // Arrange
        SendInvitationCommand command = SendInvitationCommand.builder()
                .eventId("event123")
                .inviterId("organizer123")
                .inviteeId("user456")
                .message("Come to my event!")
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(invitationRepository.existsByEventIdAndInviteeId("event123", "user456")).thenReturn(false);
        when(invitationRepository.save(any(Invitation.class))).thenReturn(Invitation.builder().build());

        // Act
        Invitation result = eventService.sendInvitation(command);

        // Assert
        assertNotNull(result);

        verify(eventRepository, atLeastOnce()).findById("event123");
        verify(invitationRepository).existsByEventIdAndInviteeId("event123", "user456");
        verify(invitationRepository).save(any(Invitation.class));
        verify(eventPublisher).publishInvitationSent("event123", "organizer123", "user456");
    }

    @Test
    void sendInvitation_NotAuthorized_ThrowsException() {
        // Arrange
        SendInvitationCommand command = SendInvitationCommand.builder()
                .eventId("event123")
                .inviterId("user456")
                .inviteeId("user789")
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));

        // Act & Assert
        EventException exception = assertThrows(
                EventException.class,
                () -> eventService.sendInvitation(command)
        );
        assertEquals("No tienes permiso para realizar esta acción", exception.getMessage());
    }

    @Test
    void checkIn_Success() {
        // Arrange
        CheckInCommand command = CheckInCommand.builder()
                .eventId("event123")
                .userId("user456")
                .qrCode("valid_qr_code")
                .build();

        RSVP rsvp = RSVP.builder()
                .eventId("event123")
                .userId("user456")
                .status(RSVP.RSVPStatus.ACCEPTED)
                .checkInQrCode("valid_qr_code")
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(rsvpRepository.findByEventIdAndUserId("event123", "user456")).thenReturn(Optional.of(rsvp));
        when(qrCodeGenerator.validateCheckInQR("valid_qr_code", "event123", "user456")).thenReturn(true);
        when(rsvpRepository.save(any(RSVP.class))).thenReturn(rsvp);

        // Act
        RSVP result = eventService.checkIn(command);

        // Assert
        assertNotNull(result);

        verify(eventRepository, atLeastOnce()).findById("event123");
        verify(rsvpRepository).findByEventIdAndUserId("event123", "user456");
        verify(qrCodeGenerator).validateCheckInQR("valid_qr_code", "event123", "user456");
        verify(rsvpRepository).save(any(RSVP.class));
        verify(eventPublisher).publishCheckIn("event123", "user456");
    }

    @Test
    void checkIn_InvalidQR_ThrowsException() {
        // Arrange
        CheckInCommand command = CheckInCommand.builder()
                .eventId("event123")
                .userId("user456")
                .qrCode("invalid_qr_code")
                .build();

        RSVP rsvp = RSVP.builder()
                .eventId("event123")
                .userId("user456")
                .status(RSVP.RSVPStatus.ACCEPTED)
                .checkInQrCode("valid_qr_code")
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(rsvpRepository.findByEventIdAndUserId("event123", "user456")).thenReturn(Optional.of(rsvp));
        when(qrCodeGenerator.validateCheckInQR("invalid_qr_code", "event123", "user456")).thenReturn(false);

        // Act & Assert
        EventException exception = assertThrows(
                EventException.class,
                () -> eventService.checkIn(command)
        );
        assertEquals("El código de check-in no es válido", exception.getMessage());
    }

    @Test
    void checkIn_AlreadyCheckedIn_ThrowsException() {
        // Arrange
        CheckInCommand command = CheckInCommand.builder()
                .eventId("event123")
                .userId("user456")
                .qrCode("valid_qr_code")
                .build();

        RSVP rsvp = RSVP.builder()
                .eventId("event123")
                .userId("user456")
                .status(RSVP.RSVPStatus.ACCEPTED)
                .checkInQrCode("valid_qr_code")
                .checkedInAt(LocalDateTime.now())
                .build();

        when(eventRepository.findById("event123")).thenReturn(Optional.of(testEvent));
        when(rsvpRepository.findByEventIdAndUserId("event123", "user456")).thenReturn(Optional.of(rsvp));

        // Act & Assert
        EventException exception = assertThrows(
                EventException.class,
                () -> eventService.checkIn(command)
        );
        assertEquals("Ya hiciste check-in en este evento", exception.getMessage());
    }

    // ---- hideAddressUntilAccepted (ADR-001, Principio IX) ----------------------------

    private Event eventHidingAddress() {
        return testEvent.toBuilder()
                .details(testEvent.getDetails().toBuilder()
                        .address("Km 5 vía Chía")
                        .latitude(4.711)
                        .longitude(-74.0721)
                        .build())
                .settings(Event.EventSettings.builder()
                        .isPublic(true)
                        .maxAttendees(100)
                        .hideAddressUntilAccepted(true)
                        .build())
                .build();
    }

    private void givenRsvp(String userId, RSVP.RSVPStatus status) {
        when(rsvpRepository.findByEventIdAndUserId("event123", userId)).thenReturn(Optional.of(
                RSVP.builder().eventId("event123").userId(userId).status(status).build()));
    }

    private static void assertAddressVisible(Event event) {
        assertEquals("Km 5 vía Chía", event.getDetails().getAddress());
        assertEquals(4.711, event.getDetails().getLatitude());
        assertEquals(-74.0721, event.getDetails().getLongitude());
    }

    private static void assertAddressHidden(Event event) {
        assertNull(event.getDetails().getAddress());
        assertNull(event.getDetails().getLatitude());
        assertNull(event.getDetails().getLongitude());
        assertEquals("Test Location", event.getDetails().getLocation());
    }

    @Test
    void getEvent_HideAddress_OrganizerSeesExactAddress() {
        when(eventRepository.findById("event123")).thenReturn(Optional.of(eventHidingAddress()));

        assertAddressVisible(eventService.getEvent("event123", "organizer123"));
        verifyNoInteractions(rsvpRepository);
    }

    @Test
    void getEvent_HideAddress_AcceptedGuestSeesExactAddress() {
        when(eventRepository.findById("event123")).thenReturn(Optional.of(eventHidingAddress()));
        givenRsvp("guest1", RSVP.RSVPStatus.ACCEPTED);

        assertAddressVisible(eventService.getEvent("event123", "guest1"));
    }

    @Test
    void getEvent_HideAddress_GuestWithoutAcceptedRsvpGetsNoExactAddress() {
        when(eventRepository.findById("event123")).thenReturn(Optional.of(eventHidingAddress()));
        givenRsvp("guest1", RSVP.RSVPStatus.MAYBE);

        assertAddressHidden(eventService.getEvent("event123", "guest1"));
    }

    @Test
    void getEvent_HideAddress_UserWithoutRsvpGetsNoExactAddress() {
        when(eventRepository.findById("event123")).thenReturn(Optional.of(eventHidingAddress()));
        when(rsvpRepository.findByEventIdAndUserId("event123", "stranger")).thenReturn(Optional.empty());

        assertAddressHidden(eventService.getEvent("event123", "stranger"));
    }

    @Test
    void getEvent_AddressNotHidden_EveryoneSeesItWithoutRsvpLookup() {
        Event visible = eventHidingAddress();
        visible.getSettings().setHideAddressUntilAccepted(false);
        when(eventRepository.findById("event123")).thenReturn(Optional.of(visible));

        assertAddressVisible(eventService.getEvent("event123", "stranger"));
        verifyNoInteractions(rsvpRepository);
    }

    @Test
    void getUpcomingEvents_HideAddress_RedactsForNonAcceptedViewer() {
        when(eventRepository.findUpcomingEvents(0, 20)).thenReturn(java.util.List.of(eventHidingAddress()));
        when(rsvpRepository.findByEventIdAndUserId("event123", "stranger")).thenReturn(Optional.empty());

        assertAddressHidden(eventService.getUpcomingEvents(0, 20, "stranger").get(0));
    }

    // T-B17: size acotado a 1..100 en /my-events y /upcoming

    @Test
    void getUpcomingEvents_SizeOver100_IsCappedAt100() {
        eventService.getUpcomingEvents(0, 500, "user1");

        verify(eventRepository).findUpcomingEvents(0, 100);
    }

    @Test
    void getUpcomingEvents_SizeZero_DoesNotBecomeUnlimited() {
        eventService.getUpcomingEvents(0, 0, "user1");

        verify(eventRepository).findUpcomingEvents(0, 1);
    }

    @Test
    void getUserEvents_SizeOver100_IsCappedAt100() {
        eventService.getUserEvents("organizer123", 2, 101);

        verify(eventRepository).findByOrganizerId("organizer123", 2, 100);
    }

    @Test
    void getUserEvents_SizeWithinLimit_IsKept() {
        eventService.getUserEvents("organizer123", 0, 100);

        verify(eventRepository).findByOrganizerId("organizer123", 0, 100);
    }

    // ---- T-B07: listEvents (ADR-014) ---------------------------------------------------

    @Test
    void listEvents_Mine_ListsEventsOrganizedByViewer() {
        when(eventRepository.findByOrganizerId("organizer123", 1, 20)).thenReturn(java.util.List.of(eventHidingAddress()));

        java.util.List<Event> events = eventService.listEvents(ListScope.MINE, "organizer123", 1, 20);

        // El organizador ve la dirección de sus eventos sin consultar RSVP
        assertAddressVisible(events.get(0));
        verify(eventRepository, never()).findUpcomingEvents(anyInt(), anyInt());
        verifyNoInteractions(rsvpRepository);
    }

    @Test
    void listEvents_Upcoming_RedactsAddressForNonAcceptedViewer() {
        when(eventRepository.findUpcomingEvents(0, 20)).thenReturn(java.util.List.of(eventHidingAddress()));
        when(rsvpRepository.findByEventIdAndUserId("event123", "stranger")).thenReturn(Optional.empty());

        assertAddressHidden(eventService.listEvents(ListScope.UPCOMING, "stranger", 0, 20).get(0));
        verify(eventRepository, never()).findByOrganizerId(any(), anyInt(), anyInt());
    }

    @Test
    void listEvents_Upcoming_AcceptedViewerSeesAddress() {
        when(eventRepository.findUpcomingEvents(0, 20)).thenReturn(java.util.List.of(eventHidingAddress()));
        givenRsvp("guest1", RSVP.RSVPStatus.ACCEPTED);

        assertAddressVisible(eventService.listEvents(ListScope.UPCOMING, "guest1", 0, 20).get(0));
    }

    @Test
    void listEvents_SizeOver100_IsValidationError() {
        EventException ex = assertThrows(EventException.class,
                () -> eventService.listEvents(ListScope.MINE, "organizer123", 0, 101));

        assertEquals(EventException.Reason.INVALID, ex.getReason());
        assertEquals(java.util.Map.of("size", "Máximo 100"), ex.getDetails());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void listEvents_Size100_IsAccepted() {
        eventService.listEvents(ListScope.UPCOMING, "user1", 0, 100);

        verify(eventRepository).findUpcomingEvents(0, 100);
    }

    @Test
    void listEvents_SizeZero_ReusesLowerBound() {
        eventService.listEvents(ListScope.MINE, "organizer123", 0, 0);

        verify(eventRepository).findByOrganizerId("organizer123", 0, 1);
    }

    @Test
    void listEvents_NegativePage_IsValidationError() {
        EventException ex = assertThrows(EventException.class,
                () -> eventService.listEvents(ListScope.UPCOMING, "user1", -1, 20));

        assertEquals(EventException.Reason.INVALID, ex.getReason());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void listScope_AcceptsOnlyContractValues() {
        assertEquals(ListScope.MINE, ListScope.fromValue("mine"));
        assertEquals(ListScope.UPCOMING, ListScope.fromValue("upcoming"));
        for (String invalid : new String[] {"MINE", "public", "all", ""}) {
            EventException ex = assertThrows(EventException.class, () -> ListScope.fromValue(invalid));
            assertEquals("El scope debe ser mine o upcoming", ex.getMessage());
        }
    }
}
