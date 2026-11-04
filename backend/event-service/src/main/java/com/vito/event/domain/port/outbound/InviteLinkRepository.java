package com.vito.event.domain.port.outbound;

import com.vito.event.domain.model.InviteLink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** T037: enlaces de invitación (colección {@code invite_links}, R4). */
public interface InviteLinkRepository {

    InviteLink save(InviteLink link);

    Optional<InviteLink> findById(String id);

    Optional<InviteLink> findByToken(String token);

    /** Enlaces del evento, los vigentes en {@code now} primero y luego los más nuevos (FR-008). */
    List<InviteLink> findByEventId(String eventId, Instant now, int page, int size);

    /**
     * Suma un uso solo si el enlace sigue vigente en {@code now}: no revocado, sin vencer y con
     * {@code maxUses == null || uses < maxUses}. Atómico, como el cupo (R1, R4).
     *
     * @return {@code false} si no se sumó
     */
    boolean tryUse(String linkId, Instant now);

    /** Devuelve un uso tomado con {@link #tryUse} que al final no se usó. */
    void releaseUse(String linkId);

    /** Revocar es irreversible e idempotente. */
    void revoke(String linkId);
}
