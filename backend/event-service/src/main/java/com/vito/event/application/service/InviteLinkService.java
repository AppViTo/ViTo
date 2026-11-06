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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * T038 (US1, R4): enlaces de invitación. El token es secreto: nunca va a los logs, solo el
 * {@code linkId}. Unirse crea la invitación ya aceptada y confirma con las reglas de cupo (FR-004).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteLinkService {

    public static final int DEFAULT_EXPIRES_IN_DAYS = 7;
    public static final int MAX_EXPIRES_IN_DAYS = 30;
    public static final int MAX_PAGE_SIZE = 100;

    private static final int TOKEN_BYTES = 16;
    private static final int TOKEN_ATTEMPTS = 3;
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{" + InviteLink.TOKEN_LENGTH + "}$");
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventRepository eventRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final InvitationRepository invitationRepository;
    private final AttendanceService attendanceService;
    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    /** El resultado de unirse: la invitación y, si se creó, la respuesta registrada. */
    public record Joined(Invitation invitation, Optional<RSVP> rsvp) { }

    public InviteLink create(String eventId, String userId, Integer expiresInDays, Integer maxUses) {
        int days = expiresInDays == null ? DEFAULT_EXPIRES_IN_DAYS : expiresInDays;
        if (days < 1 || days > MAX_EXPIRES_IN_DAYS) {
            throw EventException.invalidExpiresInDays(1, MAX_EXPIRES_IN_DAYS);
        }
        if (maxUses != null && (maxUses < 1 || maxUses > InviteLink.MAX_USES_LIMIT)) {
            throw EventException.invalidMaxUses(1, InviteLink.MAX_USES_LIMIT);
        }
        Event event = organizedEvent(eventId, userId, "Solo el organizador puede crear enlaces");
        Instant now = clock.instant();
        if (!event.isActiveAt(now)) {
            throw EventException.eventNotAcceptingGuests();
        }

        for (int attempt = 1; ; attempt++) {
            InviteLink link = InviteLink.builder()
                    .eventId(eventId)
                    .token(newToken())
                    .createdBy(userId)
                    .expiresAt(now.plus(Duration.ofDays(days)))
                    .maxUses(maxUses)
                    .uses(0)
                    .revoked(false)
                    .createdAt(now)
                    .build();
            try {
                InviteLink saved = inviteLinkRepository.save(link);
                log.info("Enlace {} creado en el evento {}", saved.getId(), eventId);
                return saved;
            } catch (DuplicateKeyException collision) {
                // 128 bits: una colisión es casi imposible, pero el índice único la detectaría
                if (attempt == TOKEN_ATTEMPTS) {
                    throw collision;
                }
            }
        }
    }

    public List<InviteLink> list(String eventId, String userId, int page, int size) {
        if (size > MAX_PAGE_SIZE) {
            throw EventException.linkPageSizeTooLarge(MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw EventException.negativePage();
        }
        organizedEvent(eventId, userId, "Solo el organizador puede ver los enlaces");
        return inviteLinkRepository.findByEventId(eventId, clock.instant(), page, Math.max(1, size));
    }

    /** Idempotente; no afecta a quienes ya se unieron (FR-008). */
    public void revoke(String eventId, String linkId, String userId) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventException::eventOrLinkNotFound);
        if (!event.getOrganizerId().equals(userId)) {
            throw EventException.onlyOrganizer("Solo el organizador puede revocar enlaces");
        }
        InviteLink link = inviteLinkRepository.findById(linkId)
                .filter(l -> eventId.equals(l.getEventId()))
                .orElseThrow(EventException::eventOrLinkNotFound);
        if (!link.isRevoked()) {
            inviteLinkRepository.revoke(link.getId());
            log.info("Enlace {} revocado en el evento {}", link.getId(), eventId);
        }
    }

    /**
     * FR-003: pública y sin dirección. Un enlace inexistente, vencido o revocado, o de un evento
     * que ya no admite invitados, da el mismo 404 (FR-006). Con el límite de usos alcanzado se
     * muestra: el 409 llega al unirse.
     */
    public InviteLinkPreview preview(String token) {
        Instant now = clock.instant();
        InviteLink link = openLink(token, now);
        Event event = eventRepository.findById(link.getEventId())
                .filter(e -> e.isActiveAt(now))
                .orElseThrow(EventException::inviteLinkUnavailable);
        return InviteLinkPreview.of(event, link);
    }

    /**
     * FR-004/FR-005: crea la invitación aceptada con {@code inviteLinkId} y confirma con las reglas
     * de cupo. Si ya tenía invitación, la devuelve sin sumar uso; el organizador no crea nada.
     */
    public Joined accept(String token, String userId, String displayName) {
        Instant now = clock.instant();
        InviteLink link = openLink(token, now);
        Event event = eventRepository.findById(link.getEventId())
                .orElseThrow(EventException::inviteLinkUnavailable);

        if (event.getOrganizerId().equals(userId)) {
            return new Joined(organizerView(event, link, now), Optional.empty());
        }
        Optional<Invitation> existing = invitationRepository.findByEventIdAndInviteeId(event.getId(), userId);
        if (existing.isPresent()) {
            return new Joined(existing.get(), Optional.empty());
        }
        if (!event.isActiveAt(now)) {
            throw EventException.eventNotAcceptingGuests();
        }
        if (!inviteLinkRepository.tryUse(link.getId(), now)) {
            // Entre la lectura y el $inc pudo vencer o revocarse; lo normal es el límite de usos
            throw EventException.inviteLinkExhausted();
        }

        Invitation invitation;
        try {
            invitation = invitationRepository.save(Invitation.builder()
                    .eventId(event.getId())
                    .inviterId(link.getCreatedBy())
                    .inviteeId(userId)
                    .status(Invitation.InvitationStatus.ACCEPTED)
                    .inviteLinkId(link.getId())
                    .sentAt(now)
                    .respondedAt(now)
                    .build());
        } catch (DuplicateKeyException sameUserTwice) {
            // Otra petición de la misma persona se unió primero: ese uso no cuenta
            inviteLinkRepository.releaseUse(link.getId());
            return new Joined(invitationRepository.findByEventIdAndInviteeId(event.getId(), userId)
                    .orElseThrow(() -> sameUserTwice), Optional.empty());
        }
        eventRepository.incrementStat(event.getId(), StatCounter.INVITED, 1);

        RSVP rsvp = attendanceService.confirm(event.getId(), userId, displayName, null);
        eventPublisher.publishRSVPChanged(event.getId(), userId, rsvp.getStatus().name());
        log.info("Unión por el enlace {} al evento {}: {}", link.getId(), event.getId(), rsvp.getStatus());
        return new Joined(invitation, Optional.of(rsvp));
    }

    /** Token con forma válida y enlace sin revocar ni vencer; si no, el 404 único (FR-006). */
    private InviteLink openLink(String token, Instant now) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw EventException.inviteLinkUnavailable();
        }
        return inviteLinkRepository.findByToken(token)
                .filter(link -> !link.isRevoked() && link.getExpiresAt() != null && link.getExpiresAt().isAfter(now))
                .orElseThrow(EventException::inviteLinkUnavailable);
    }

    private Event organizedEvent(String eventId, String userId, String forbiddenMessage) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventException::eventNotFound);
        if (!event.getOrganizerId().equals(userId)) {
            throw EventException.onlyOrganizer(forbiddenMessage);
        }
        return event;
    }

    /** FR-005: el organizador "se une" con éxito sin crear nada; esta invitación no se guarda. */
    private static Invitation organizerView(Event event, InviteLink link, Instant now) {
        return Invitation.builder()
                .eventId(event.getId())
                .inviterId(event.getOrganizerId())
                .inviteeId(event.getOrganizerId())
                .status(Invitation.InvitationStatus.ACCEPTED)
                .inviteLinkId(link.getId())
                .sentAt(event.getCreatedAt() != null ? event.getCreatedAt() : now)
                .build();
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return BASE64URL.encodeToString(bytes);
    }
}
