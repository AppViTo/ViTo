package com.vito.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enlace de invitación de un evento (Ola 1, RFC #204). El {@code token} es secreto: nunca va
 * a logs ni trazas (R4). La {@code url} pública no se guarda; se arma con {@code INVITE_LINK_BASE_URL}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class InviteLink {

    public static final int TOKEN_LENGTH = 22;
    public static final int MAX_USES_LIMIT = 1000;

    private String id;

    private String eventId;

    /** 128 bits aleatorios en base64url sin relleno: 22 caracteres. */
    private String token;

    private String createdBy;

    private Instant expiresAt;

    /** 1–1000; null = sin límite. */
    private Integer maxUses;

    private int uses;

    /** Revocar es irreversible. */
    private boolean revoked;

    private Instant createdAt;

    /** Vigente por sí mismo; la vigencia del evento se comprueba aparte. */
    public boolean isUsableAt(Instant now) {
        return !revoked
                && expiresAt != null && expiresAt.isAfter(now)
                && (maxUses == null || uses < maxUses);
    }
}
