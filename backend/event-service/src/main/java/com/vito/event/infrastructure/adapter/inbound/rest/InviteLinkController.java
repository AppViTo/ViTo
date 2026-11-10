package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.InviteLinkService;
import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.InviteLink;
import com.vito.event.domain.model.InviteLinkPreview;
import com.vito.event.infrastructure.config.InviteLinkSettings;
import com.vito.shared.web.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * T039 (US1): las 5 operaciones de enlaces. Gestión solo para el organizador; la vista previa
 * es pública (SecurityConfig). El token no se registra en ningún log (R4).
 */
@RestController
@RequiredArgsConstructor
public class InviteLinkController {

    static final String LOGIN_REQUIRED = "Debes iniciar sesión para realizar esta acción";

    private final InviteLinkService inviteLinkService;
    private final InviteLinkSettings inviteLinkSettings;

    /** Cuerpo opcional: sin él, 7 días y sin límite de usos. */
    public record CreateInviteLinkRequest(Integer expiresInDays, Integer maxUses) { }

    /** {@code InviteLink} del contrato, con la {@code url} armada desde la configuración. */
    public record InviteLinkResponse(String id, String eventId, String token, String url, Instant expiresAt,
                                     Integer maxUses, int uses, boolean revoked, Instant createdAt) { }

    @PostMapping("/api/v1/events/{eventId}/invite-links")
    public ResponseEntity<InviteLinkResponse> createInviteLink(
            @PathVariable String eventId,
            @RequestBody(required = false) CreateInviteLinkRequest request) {
        String userId = currentUser().id();
        InviteLink link = inviteLinkService.create(eventId, userId,
                request == null ? null : request.expiresInDays(),
                request == null ? null : request.maxUses());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
    }

    @GetMapping("/api/v1/events/{eventId}/invite-links")
    public ResponseEntity<List<InviteLinkResponse>> listInviteLinks(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = currentUser().id();
        return ResponseEntity.ok(inviteLinkService.list(eventId, userId, page, size).stream()
                .map(this::toResponse)
                .toList());
    }

    @DeleteMapping("/api/v1/events/{eventId}/invite-links/{linkId}")
    public ResponseEntity<Void> revokeInviteLink(@PathVariable String eventId, @PathVariable String linkId) {
        inviteLinkService.revoke(eventId, linkId, currentUser().id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/invite-links/{token}")
    public ResponseEntity<InviteLinkPreview> getInviteLinkPreview(@PathVariable String token) {
        return ResponseEntity.ok(inviteLinkService.preview(token));
    }

    @PostMapping("/api/v1/invite-links/{token}/accept")
    public ResponseEntity<InvitationResponse> acceptInviteLink(@PathVariable String token) {
        AuthenticatedUser user = currentUser();
        InviteLinkService.Joined joined = inviteLinkService.accept(token, user.id(), user.displayName());
        return ResponseEntity.ok(InvitationResponse.from(joined.invitation()));
    }

    private InviteLinkResponse toResponse(InviteLink link) {
        return new InviteLinkResponse(link.getId(), link.getEventId(), link.getToken(),
                inviteLinkSettings.linkUrl(link.getToken()), link.getExpiresAt(), link.getMaxUses(),
                link.getUses(), link.isRevoked(), link.getCreatedAt());
    }

    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw EventException.unauthenticated(LOGIN_REQUIRED);
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user : new AuthenticatedUser(authentication.getName(), null);
    }
}
