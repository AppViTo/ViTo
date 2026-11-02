package com.vito.event.domain.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** T020 (FR-028): las fechas de invitaciones, RSVP y enlaces salen como instante UTC con {@code Z}. */
class Ola1ModelJsonTest {

    private static final Instant WHEN = Instant.parse("2026-11-01T18:30:00Z");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void invitationDatesAreUtcInstants() {
        JsonNode json = mapper.valueToTree(Invitation.builder()
                .sentAt(WHEN).expiresAt(WHEN.plusSeconds(60)).respondedAt(WHEN).inviteLinkId("link-1").build());

        assertEquals("2026-11-01T18:30:00Z", json.get("sentAt").asString());
        assertEquals("2026-11-01T18:31:00Z", json.get("expiresAt").asString());
        assertEquals("link-1", json.get("inviteLinkId").asString());
    }

    @Test
    void rsvpDatesAreUtcInstantsAndGuestsCountIsGone() {
        JsonNode json = mapper.valueToTree(RSVP.builder()
                .displayName("Ana").respondedAt(WHEN).waitlistedAt(WHEN).build());

        assertEquals("2026-11-01T18:30:00Z", json.get("respondedAt").asString());
        assertEquals("2026-11-01T18:30:00Z", json.get("waitlistedAt").asString());
        assertEquals("Ana", json.get("displayName").asString());
        assertFalse(json.has("guestsCount"));
    }

    @Test
    void inviteLinkDatesAreUtcInstants() {
        JsonNode json = mapper.valueToTree(InviteLink.builder().expiresAt(WHEN).createdAt(WHEN).build());

        assertEquals("2026-11-01T18:30:00Z", json.get("expiresAt").asString());
        assertEquals("2026-11-01T18:30:00Z", json.get("createdAt").asString());
    }

    @Test
    void inviteLinkUsability() {
        InviteLink link = InviteLink.builder().expiresAt(WHEN.plusSeconds(3600)).maxUses(2).uses(1).build();

        assertEquals(true, link.isUsableAt(WHEN));
        assertEquals(false, link.toBuilder().uses(2).build().isUsableAt(WHEN));
        assertEquals(false, link.toBuilder().revoked(true).build().isUsableAt(WHEN));
        assertEquals(false, link.isUsableAt(WHEN.plusSeconds(3600)));
        assertEquals(true, link.toBuilder().maxUses(null).uses(500).build().isUsableAt(WHEN));
    }
}
