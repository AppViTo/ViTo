package com.vito.event.application.service;

import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.EventRepository.StatCounter;
import com.vito.event.domain.port.outbound.RSVPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * T024 (R1): cupo y lista de espera, compartido por el RSVP y la unión por enlace.
 * El cupo vive en {@code stats.totalAccepted} del evento y solo cambia con {@code $inc}
 * condicional ({@link EventRepository#tryReserveSeat}); nunca se lee, compara y reescribe.
 * Quien llama valida antes el evento (existe, publicado, acceso).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EventRepository eventRepository;
    private final RSVPRepository rsvpRepository;
    private final EventPublisherPort eventPublisher;

    /**
     * Confirma la asistencia: ocupa un lugar o deja la respuesta en {@code WAITLISTED} con
     * {@code waitlistedAt}. Si ya estaba {@code ACCEPTED} o {@code WAITLISTED} no cambia nada (FR-019).
     */
    public RSVP confirm(String eventId, String userId, String displayName, String message) {
        Optional<RSVP> existing = rsvpRepository.findByEventIdAndUserId(eventId, userId);
        RSVP.RSVPStatus previous = existing.map(RSVP::getStatus).orElse(null);
        if (previous == RSVP.RSVPStatus.ACCEPTED || previous == RSVP.RSVPStatus.WAITLISTED) {
            return existing.get();
        }

        boolean seat = eventRepository.tryReserveSeat(eventId);
        Instant now = Instant.now();
        RSVP rsvp = base(existing, eventId, userId, displayName)
                .status(seat ? RSVP.RSVPStatus.ACCEPTED : RSVP.RSVPStatus.WAITLISTED)
                .waitlistedAt(seat ? null : now)
                .message(message)
                .respondedAt(now)
                .build();

        RSVP saved;
        try {
            saved = rsvpRepository.save(rsvp);
        } catch (DuplicateKeyException e) {
            // Otra petición de la misma persona creó el RSVP primero: se devuelve ese y el lugar no se usa
            if (seat) {
                eventRepository.releaseSeat(eventId);
            }
            return rsvpRepository.findByEventIdAndUserId(eventId, userId).orElseThrow(() -> e);
        }

        if (!seat) {
            eventRepository.incrementStat(eventId, StatCounter.WAITLISTED, 1);
        }
        leave(eventId, previous);
        return saved;
    }

    /**
     * Cambia la respuesta a {@code DECLINED} o {@code MAYBE}. Si tenía lugar, lo libera y
     * promueve al {@code WAITLISTED} más antiguo; la misma respuesta otra vez no cambia nada.
     */
    public RSVP release(String eventId, String userId, RSVP.RSVPStatus newStatus, String displayName, String message) {
        StatCounter counter = switch (newStatus) {
            case DECLINED -> StatCounter.DECLINED;
            case MAYBE -> StatCounter.MAYBE;
            default -> throw new IllegalArgumentException("release solo admite DECLINED o MAYBE: " + newStatus);
        };

        Optional<RSVP> existing = rsvpRepository.findByEventIdAndUserId(eventId, userId);
        RSVP.RSVPStatus previous = existing.map(RSVP::getStatus).orElse(null);
        if (previous == newStatus) {
            return existing.get();
        }

        RSVP rsvp = base(existing, eventId, userId, displayName)
                .status(newStatus)
                .waitlistedAt(null)
                .message(message)
                .respondedAt(Instant.now())
                .build();

        RSVP saved;
        try {
            saved = rsvpRepository.save(rsvp);
        } catch (DuplicateKeyException e) {
            return rsvpRepository.findByEventIdAndUserId(eventId, userId).orElseThrow(() -> e);
        }

        eventRepository.incrementStat(eventId, counter, 1);
        // El lugar se libera después de guardar: nunca hay más confirmados que el cupo
        leave(eventId, previous);
        return saved;
    }

    /** Descuenta la respuesta anterior; si era {@code ACCEPTED}, libera el lugar y promueve. */
    private void leave(String eventId, RSVP.RSVPStatus previous) {
        if (previous == null) {
            return;
        }
        switch (previous) {
            case ACCEPTED -> {
                eventRepository.releaseSeat(eventId);
                promoteNext(eventId);
            }
            case WAITLISTED -> eventRepository.incrementStat(eventId, StatCounter.WAITLISTED, -1);
            case DECLINED -> eventRepository.incrementStat(eventId, StatCounter.DECLINED, -1);
            case MAYBE -> eventRepository.incrementStat(eventId, StatCounter.MAYBE, -1);
            case PENDING -> { }
        }
    }

    /** Promueve al {@code WAITLISTED} más antiguo; si el lugar ya lo tomó otra persona, vuelve a la lista. */
    private void promoteNext(String eventId) {
        Optional<RSVP> promoted = rsvpRepository.promoteOldestWaitlisted(eventId);
        if (promoted.isEmpty()) {
            return;
        }
        RSVP before = promoted.get();
        if (eventRepository.tryReserveSeat(eventId)) {
            eventRepository.incrementStat(eventId, StatCounter.WAITLISTED, -1);
            eventPublisher.publishRSVPChanged(eventId, before.getUserId(), RSVP.RSVPStatus.ACCEPTED.name());
            log.info("Lista de espera: RSVP {} promovido en el evento {}", before.getId(), eventId);
        } else {
            rsvpRepository.revertToWaitlisted(before.getId(), before.getWaitlistedAt());
        }
    }

    private static RSVP.RSVPBuilder base(Optional<RSVP> existing, String eventId, String userId, String displayName) {
        RSVP.RSVPBuilder builder = existing.map(RSVP::toBuilder)
                .orElseGet(() -> RSVP.builder().eventId(eventId).userId(userId));
        // Sin X-User-Name se conserva el nombre guardado
        if (displayName != null) {
            builder.displayName(displayName);
        }
        return builder;
    }
}
