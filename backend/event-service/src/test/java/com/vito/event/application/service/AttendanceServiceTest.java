package com.vito.event.application.service;

import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.EventRepository.StatCounter;
import com.vito.event.domain.port.outbound.RSVPRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** T024 (R1): confirmar ocupa lugar o deja en lista de espera; liberar promueve al más antiguo. */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final String EVENT = "event-1";
    private static final String USER = "user-1";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RSVPRepository rsvpRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void confirmWithSeatAccepts() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.empty());
        when(eventRepository.tryReserveSeat(EVENT)).thenReturn(true);
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));

        RSVP rsvp = attendanceService.confirm(EVENT, USER, "Ana", null);

        assertEquals(RSVP.RSVPStatus.ACCEPTED, rsvp.getStatus());
        assertEquals("Ana", rsvp.getDisplayName());
        assertNull(rsvp.getWaitlistedAt());
        assertNotNull(rsvp.getRespondedAt());
        verify(eventRepository, never()).incrementStat(any(), any(), anyInt());
    }

    @Test
    void confirmWithoutSeatWaitlists() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.empty());
        when(eventRepository.tryReserveSeat(EVENT)).thenReturn(false);
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));

        RSVP rsvp = attendanceService.confirm(EVENT, USER, "Ana", null);

        assertEquals(RSVP.RSVPStatus.WAITLISTED, rsvp.getStatus());
        assertNotNull(rsvp.getWaitlistedAt());
        verify(eventRepository).incrementStat(EVENT, StatCounter.WAITLISTED, 1);
    }

    @Test
    void confirmIsIdempotentWhenAlreadyAcceptedOrWaitlisted() {
        for (RSVP.RSVPStatus status : new RSVP.RSVPStatus[]{RSVP.RSVPStatus.ACCEPTED, RSVP.RSVPStatus.WAITLISTED}) {
            RSVP existing = rsvp(status);
            when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(existing));

            assertSame(existing, attendanceService.confirm(EVENT, USER, "Ana", null));
        }
        verify(eventRepository, never()).tryReserveSeat(any());
        verify(rsvpRepository, never()).save(any());
    }

    @Test
    void confirmFromDeclinedMovesTheCounter() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(rsvp(RSVP.RSVPStatus.DECLINED)));
        when(eventRepository.tryReserveSeat(EVENT)).thenReturn(true);
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));

        RSVP rsvp = attendanceService.confirm(EVENT, USER, "Ana", null);

        assertEquals(RSVP.RSVPStatus.ACCEPTED, rsvp.getStatus());
        assertEquals("rsvp-1", rsvp.getId());
        verify(eventRepository).incrementStat(EVENT, StatCounter.DECLINED, -1);
    }

    @Test
    void confirmReleasesTheSeatWhenAConcurrentInsertWon() {
        RSVP stored = rsvp(RSVP.RSVPStatus.ACCEPTED);
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(stored));
        when(eventRepository.tryReserveSeat(EVENT)).thenReturn(true);
        when(rsvpRepository.save(any(RSVP.class))).thenThrow(new DuplicateKeyException("eventId_userId_unique"));

        assertSame(stored, attendanceService.confirm(EVENT, USER, "Ana", null));
        verify(eventRepository).releaseSeat(EVENT);
    }

    @Test
    void releaseFromAcceptedFreesTheSeatAndPromotesTheOldest() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(rsvp(RSVP.RSVPStatus.ACCEPTED)));
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));
        RSVP waiting = RSVP.builder().id("rsvp-2").eventId(EVENT).userId("user-2")
                .status(RSVP.RSVPStatus.WAITLISTED).waitlistedAt(Instant.parse("2026-11-01T10:00:00Z")).build();
        when(rsvpRepository.promoteOldestWaitlisted(EVENT)).thenReturn(Optional.of(waiting));
        when(eventRepository.tryReserveSeat(EVENT)).thenReturn(true);

        RSVP rsvp = attendanceService.release(EVENT, USER, RSVP.RSVPStatus.DECLINED, "Ana", null);

        assertEquals(RSVP.RSVPStatus.DECLINED, rsvp.getStatus());
        var order = inOrder(rsvpRepository, eventRepository);
        order.verify(rsvpRepository).save(any(RSVP.class));
        order.verify(eventRepository).releaseSeat(EVENT);
        order.verify(rsvpRepository).promoteOldestWaitlisted(EVENT);
        verify(eventRepository).incrementStat(EVENT, StatCounter.DECLINED, 1);
        verify(eventRepository).incrementStat(EVENT, StatCounter.WAITLISTED, -1);
        verify(eventPublisher).publishRSVPChanged(EVENT, "user-2", "ACCEPTED");
        verify(rsvpRepository, never()).revertToWaitlisted(any(), any());
    }

    @Test
    void promotionWithoutSeatGoesBackToTheWaitlist() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(rsvp(RSVP.RSVPStatus.ACCEPTED)));
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant since = Instant.parse("2026-11-01T10:00:00Z");
        RSVP waiting = RSVP.builder().id("rsvp-2").eventId(EVENT).userId("user-2")
                .status(RSVP.RSVPStatus.WAITLISTED).waitlistedAt(since).build();
        when(rsvpRepository.promoteOldestWaitlisted(EVENT)).thenReturn(Optional.of(waiting));
        when(eventRepository.tryReserveSeat(EVENT)).thenReturn(false);

        attendanceService.release(EVENT, USER, RSVP.RSVPStatus.MAYBE, "Ana", null);

        verify(rsvpRepository).revertToWaitlisted("rsvp-2", since);
        verify(eventRepository, never()).incrementStat(EVENT, StatCounter.WAITLISTED, -1);
        verify(eventPublisher, never()).publishRSVPChanged(any(), any(), any());
    }

    @Test
    void releaseFromWaitlistedLeavesTheQueueWithoutTouchingTheSeat() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(rsvp(RSVP.RSVPStatus.WAITLISTED)));
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));

        RSVP rsvp = attendanceService.release(EVENT, USER, RSVP.RSVPStatus.DECLINED, "Ana", null);

        assertNull(rsvp.getWaitlistedAt());
        verify(eventRepository).incrementStat(EVENT, StatCounter.WAITLISTED, -1);
        verify(eventRepository, never()).releaseSeat(any());
        verify(rsvpRepository, never()).promoteOldestWaitlisted(any());
    }

    @Test
    void releaseWithTheSameAnswerChangesNothing() {
        RSVP existing = rsvp(RSVP.RSVPStatus.MAYBE);
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(existing));

        assertSame(existing, attendanceService.release(EVENT, USER, RSVP.RSVPStatus.MAYBE, "Ana", null));
        verify(rsvpRepository, never()).save(any());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void releaseOnlyAcceptsDeclinedOrMaybe() {
        assertThrows(IllegalArgumentException.class,
                () -> attendanceService.release(EVENT, USER, RSVP.RSVPStatus.ACCEPTED, "Ana", null));
    }

    @Test
    void savedRsvpKeepsTheDisplayNameWhenTheHeaderIsMissing() {
        RSVP existing = rsvp(RSVP.RSVPStatus.MAYBE).toBuilder().displayName("Ana").build();
        when(rsvpRepository.findByEventIdAndUserId(EVENT, USER)).thenReturn(Optional.of(existing));
        when(rsvpRepository.save(any(RSVP.class))).thenAnswer(inv -> inv.getArgument(0));

        attendanceService.release(EVENT, USER, RSVP.RSVPStatus.DECLINED, null, null);

        ArgumentCaptor<RSVP> saved = ArgumentCaptor.forClass(RSVP.class);
        verify(rsvpRepository).save(saved.capture());
        assertEquals("Ana", saved.getValue().getDisplayName());
        verify(eventRepository).incrementStat(EVENT, StatCounter.MAYBE, -1);
    }

    private static RSVP rsvp(RSVP.RSVPStatus status) {
        return RSVP.builder().id("rsvp-1").eventId(EVENT).userId(USER).status(status)
                .respondedAt(Instant.parse("2026-11-01T09:00:00Z"))
                .waitlistedAt(status == RSVP.RSVPStatus.WAITLISTED ? Instant.parse("2026-11-01T09:00:00Z") : null)
                .build();
    }
}
