package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T050 y T056 contra un MongoDB real: orden estable de las páginas, filtro por estado e índice único. */
@DataMongoTest
@Import({InvitationMongoAdapter.class, RSVPMongoAdapter.class})
class InvitationsAndAttendeesMongoIntegrationTest extends MongoContainerTest {

    private static final Instant T0 = Instant.parse("2026-10-28T02:00:00Z");

    @Autowired private InvitationMongoAdapter invitations;
    @Autowired private RSVPMongoAdapter rsvps;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), "invitations");
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), "rsvps");
        invitations.ensureIndexes();
    }

    private void invitation(String id, String eventId, String invitee, int minutes) {
        invitations.save(Invitation.builder().id(id).eventId(eventId).inviterId("usr_org").inviteeId(invitee)
                .status(Invitation.InvitationStatus.PENDING).sentAt(T0.plusSeconds(minutes * 60L)).build());
    }

    private void rsvp(String id, String userId, RSVP.RSVPStatus status, int minutes) {
        rsvps.save(RSVP.builder().id(id).eventId("evt_1").userId(userId).status(status)
                .respondedAt(T0.plusSeconds(minutes * 60L)).build());
    }

    @Test
    void invitationsComeNewestFirstAndPageWithoutRepeating() {
        invitation("inv_a", "evt_1", "usr_a", 1);
        invitation("inv_b", "evt_1", "usr_b", 3);
        invitation("inv_c", "evt_1", "usr_c", 2);
        invitation("inv_d", "evt_2", "usr_a", 9);

        List<String> firstPage = invitations.findByEventId("evt_1", 0, 2).stream().map(Invitation::getId).toList();
        List<String> secondPage = invitations.findByEventId("evt_1", 1, 2).stream().map(Invitation::getId).toList();

        assertEquals(List.of("inv_b", "inv_c"), firstPage);
        assertEquals(List.of("inv_a"), secondPage);
    }

    @Test
    void theUniqueIndexRejectsASecondInvitationToTheSamePerson() {
        invitation("inv_a", "evt_1", "usr_a", 1);

        assertThrows(DuplicateKeyException.class, () -> invitation("inv_b", "evt_1", "usr_a", 2));
    }

    @Test
    void attendeesComeInResponseOrderAndFilterByStatus() {
        rsvp("r1", "usr_a", RSVP.RSVPStatus.WAITLISTED, 5);
        rsvp("r2", "usr_b", RSVP.RSVPStatus.ACCEPTED, 1);
        rsvp("r3", "usr_c", RSVP.RSVPStatus.ACCEPTED, 3);
        rsvp("r4", "usr_d", RSVP.RSVPStatus.DECLINED, 2);

        List<String> all = rsvps.findByEventId("evt_1", null, 0, 20).stream().map(RSVP::getUserId).toList();
        List<String> accepted = rsvps.findByEventId("evt_1", RSVP.RSVPStatus.ACCEPTED, 0, 20).stream()
                .map(RSVP::getUserId).toList();
        List<String> secondPage = rsvps.findByEventId("evt_1", null, 1, 3).stream().map(RSVP::getUserId).toList();

        assertEquals(List.of("usr_b", "usr_d", "usr_c", "usr_a"), all);
        assertEquals(List.of("usr_b", "usr_c"), accepted);
        assertEquals(List.of("usr_a"), secondPage);
    }
}
