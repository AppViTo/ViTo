package com.vito.checkin.application.service;

import com.vito.checkin.domain.exception.CheckInException;
import com.vito.checkin.domain.model.CheckInEvent;
import com.vito.checkin.domain.model.CheckInTicket;
import com.vito.checkin.domain.port.inbound.CheckInService.*;
import com.vito.checkin.domain.port.outbound.*;
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
class CheckInServiceImplTest {

    @Mock
    private CheckInTicketRepository ticketRepository;

    @Mock
    private QRCodeService qrCodeService;

    @Mock
    private CheckInEventPublisher eventPublisher;

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private CheckInServiceImpl checkInService;

    private CheckInTicket testTicket;

    @BeforeEach
    void setUp() {
        testTicket = CheckInTicket.builder()
                .id("ticket123")
                .eventId("event123")
                .userId("user123")
                .qrCode("test_qr_code")
                .qrCodeHash("test_hash")
                .status(CheckInTicket.CheckInStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }

    @Test
    void generateTicket_Success() {
        // Arrange
        GenerateTicketCommand command = GenerateTicketCommand.builder()
                .eventId("event123")
                .userId("user123")
                .build();

        when(ticketRepository.existsByEventIdAndUserId("event123", "user123")).thenReturn(false);
        when(qrCodeService.generateQRCode("event123", "user123")).thenReturn("test_qr_code");
        when(qrCodeService.generateQRCodeHash("test_qr_code")).thenReturn("test_hash");
        when(ticketRepository.save(any(CheckInTicket.class))).thenReturn(testTicket);

        // Act
        CheckInTicket result = checkInService.generateTicket(command);

        // Assert
        assertNotNull(result);
        assertEquals("event123", result.getEventId());
        assertEquals("user123", result.getUserId());

        verify(ticketRepository).existsByEventIdAndUserId("event123", "user123");
        verify(qrCodeService).generateQRCode("event123", "user123");
        verify(ticketRepository).save(any(CheckInTicket.class));
    }

    @Test
    void generateTicket_AlreadyHasTicket_ThrowsException() {
        // Arrange
        GenerateTicketCommand command = GenerateTicketCommand.builder()
                .eventId("event123")
                .userId("user123")
                .build();

        when(ticketRepository.existsByEventIdAndUserId("event123", "user123")).thenReturn(true);

        // Act & Assert
        CheckInException exception = assertThrows(
                CheckInException.class,
                () -> checkInService.generateTicket(command)
        );
        assertEquals("Ya tienes un ticket de check-in para este evento", exception.getMessage());

        verify(ticketRepository).existsByEventIdAndUserId("event123", "user123");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void validateAndCheckIn_Success() {
        // Arrange
        ValidateCheckInCommand command = ValidateCheckInCommand.builder()
                .qrCode("test_qr_code")
                .eventId("event123")
                .location("Main Entrance")
                .latitude(40.7128)
                .longitude(-74.0060)
                .build();

        when(ticketRepository.findByQrCode("test_qr_code")).thenReturn(Optional.of(testTicket));
        when(qrCodeService.validateQRCode("test_qr_code", "event123", "user123")).thenReturn(true);
        when(ticketRepository.save(any(CheckInTicket.class))).thenReturn(testTicket);
        when(userProfileService.getUserProfile("user123")).thenReturn(Optional.of(
                CheckInEvent.UserProfile.builder()
                        .userId("user123")
                        .username("testuser")
                        .displayName("Test User")
                        .build()));
        when(ticketRepository.countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.USED)).thenReturn(5L);

        // Act
        CheckInResult result = checkInService.validateAndCheckIn(command);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Check-in successful", result.getMessage());

        verify(ticketRepository).findByQrCode("test_qr_code");
        verify(qrCodeService).validateQRCode("test_qr_code", "event123", "user123");
        verify(ticketRepository).save(any(CheckInTicket.class));
        verify(eventPublisher).publishCheckIn(any(CheckInEvent.class));
    }

    @Test
    void validateAndCheckIn_InvalidQRCode_ThrowsException() {
        // Arrange
        ValidateCheckInCommand command = ValidateCheckInCommand.builder()
                .qrCode("invalid_qr_code")
                .eventId("event123")
                .build();

        when(ticketRepository.findByQrCode("invalid_qr_code")).thenReturn(Optional.empty());

        // Act & Assert
        CheckInException exception = assertThrows(
                CheckInException.class,
                () -> checkInService.validateAndCheckIn(command)
        );
        assertEquals("El código QR no es válido", exception.getMessage());
    }

    @Test
    void validateAndCheckIn_TicketAlreadyUsed_ThrowsException() {
        // Arrange
        CheckInTicket usedTicket = CheckInTicket.builder()
                .id("ticket123")
                .eventId("event123")
                .userId("user123")
                .qrCode("test_qr_code")
                .status(CheckInTicket.CheckInStatus.USED)
                .checkedInAt(LocalDateTime.now())
                .build();

        ValidateCheckInCommand command = ValidateCheckInCommand.builder()
                .qrCode("test_qr_code")
                .eventId("event123")
                .build();

        when(ticketRepository.findByQrCode("test_qr_code")).thenReturn(Optional.of(usedTicket));

        // Act & Assert
        CheckInException exception = assertThrows(
                CheckInException.class,
                () -> checkInService.validateAndCheckIn(command)
        );
        assertEquals("El ticket de check-in ya se usó", exception.getMessage());
    }

    @Test
    void validateAndCheckIn_TicketExpired_ThrowsException() {
        // Arrange
        CheckInTicket expiredTicket = CheckInTicket.builder()
                .id("ticket123")
                .eventId("event123")
                .userId("user123")
                .qrCode("test_qr_code")
                .status(CheckInTicket.CheckInStatus.EXPIRED)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        ValidateCheckInCommand command = ValidateCheckInCommand.builder()
                .qrCode("test_qr_code")
                .eventId("event123")
                .build();

        when(ticketRepository.findByQrCode("test_qr_code")).thenReturn(Optional.of(expiredTicket));

        // Act & Assert
        CheckInException exception = assertThrows(
                CheckInException.class,
                () -> checkInService.validateAndCheckIn(command)
        );
        assertEquals("El ticket de check-in expiró", exception.getMessage());
    }

    @Test
    void getEventStats_Success() {
        // Arrange
        when(ticketRepository.countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.ACTIVE)).thenReturn(10L);
        when(ticketRepository.countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.USED)).thenReturn(5L);
        when(ticketRepository.countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.EXPIRED)).thenReturn(2L);
        when(ticketRepository.countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.REVOKED)).thenReturn(1L);

        // Act
        CheckInStats result = checkInService.getEventStats("event123");

        // Assert
        assertNotNull(result);
        assertEquals("event123", result.getEventId());
        assertEquals(18, result.getTotalExpected());
        assertEquals(5, result.getTotalCheckedIn());
        assertEquals(10, result.getTotalPending());
        assertEquals(2, result.getTotalExpired());
        assertEquals(1, result.getTotalRevoked());
        assertEquals(27.78, result.getCheckInRate(), 0.01);

        verify(ticketRepository).countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.ACTIVE);
        verify(ticketRepository).countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.USED);
        verify(ticketRepository).countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.EXPIRED);
        verify(ticketRepository).countByEventIdAndStatus("event123", CheckInTicket.CheckInStatus.REVOKED);
    }
}
