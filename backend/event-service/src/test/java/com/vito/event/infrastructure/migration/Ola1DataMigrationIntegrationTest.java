package com.vito.event.infrastructure.migration;

import com.vito.event.infrastructure.adapter.outbound.persistence.InvitationMongoAdapter;
import com.vito.event.infrastructure.adapter.outbound.persistence.RSVPMongoAdapter;
import com.vito.event.support.MongoContainerTest;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** T066: duplicados, contadores, waitlistedAt y fechas en texto; una sola vez; luego los índices de T021. */
@DataMongoTest
@Import({Ola1DataMigration.class, RSVPMongoAdapter.class, InvitationMongoAdapter.class})
class Ola1DataMigrationIntegrationTest extends MongoContainerTest {

    @Autowired
    private Ola1DataMigration migration;

    @Autowired
    private RSVPMongoAdapter rsvpAdapter;

    @Autowired
    private InvitationMongoAdapter invitationAdapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    private ObjectId eventId;

    @BeforeEach
    void seed() {
        for (String collection : new String[]{"events", "rsvps", "invitations", Ola1DataMigration.MIGRATIONS}) {
            mongoTemplate.dropCollection(collection);
        }
        eventId = new ObjectId();
        String id = eventId.toHexString();
        // Contadores desfasados por la reescritura anterior del documento
        mongoTemplate.insert(new Document("_id", eventId).append("title", "Evento")
                .append("stats", new Document("totalAccepted", 7).append("totalInvited", 9)), "events");

        // u1 respondió dos veces: queda la respuesta más reciente (DECLINED)
        mongoTemplate.insert(rsvp(id, "u1", "ACCEPTED", date("2026-10-30T10:00:00Z")), "rsvps");
        mongoTemplate.insert(rsvp(id, "u1", "DECLINED", date("2026-10-30T11:00:00Z")), "rsvps");
        mongoTemplate.insert(rsvp(id, "u2", "WAITLISTED", date("2026-10-30T09:00:00Z")), "rsvps");
        // Fecha guardada como texto con hora local
        mongoTemplate.insert(rsvp(id, "u3", "ACCEPTED", "2026-10-30T12:00:00"), "rsvps");

        mongoTemplate.insert(new Document("eventId", id).append("inviteeId", "u1").append("status", "PENDING")
                .append("sentAt", date("2026-10-29T08:00:00Z")), "invitations");
        mongoTemplate.insert(new Document("eventId", id).append("inviteeId", "u1").append("status", "DECLINED")
                .append("sentAt", date("2026-10-29T08:00:00Z")).append("respondedAt", date("2026-10-30T11:00:00Z")),
                "invitations");
        mongoTemplate.insert(new Document("eventId", id).append("inviteeId", "u2").append("status", "PENDING")
                .append("sentAt", date("2026-10-29T09:00:00Z")), "invitations");
    }

    @Test
    void cleansTheDataAndRecalculatesTheCounters() {
        assertTrue(migration.run());

        String id = eventId.toHexString();
        assertEquals(3, mongoTemplate.count(new Query(Criteria.where("eventId").is(id)), "rsvps"));
        assertEquals("DECLINED", rsvp(id, "u1").getString("status"));
        assertEquals(2, mongoTemplate.count(new Query(Criteria.where("eventId").is(id)), "invitations"));
        assertEquals("DECLINED", mongoTemplate.findOne(new Query(Criteria.where("inviteeId").is("u1")),
                Document.class, "invitations").getString("status"));

        assertEquals(date("2026-10-30T09:00:00Z"), rsvp(id, "u2").getDate("waitlistedAt"));
        assertEquals(date("2026-10-30T12:00:00Z"), rsvp(id, "u3").get("respondedAt"));

        Document stats = mongoTemplate.findById(eventId, Document.class, "events").get("stats", Document.class);
        assertEquals(1, stats.getInteger("totalAccepted"));
        assertEquals(1, stats.getInteger("totalDeclined"));
        assertEquals(0, stats.getInteger("totalMaybe"));
        assertEquals(1, stats.getInteger("totalWaitlisted"));
        assertEquals(2, stats.getInteger("totalInvited"));

        Document record = mongoTemplate.findById(Ola1DataMigration.ID, Document.class, Ola1DataMigration.MIGRATIONS);
        assertEquals(1, record.getInteger("rsvpsRemoved"));
        assertEquals(1, record.getInteger("invitationsRemoved"));
    }

    @Test
    void runsOnlyOnce() {
        assertTrue(migration.run());
        mongoTemplate.insert(rsvp(eventId.toHexString(), "u4", "ACCEPTED", date("2026-10-31T10:00:00Z")), "rsvps");

        assertFalse(migration.run());
        Document stats = mongoTemplate.findById(eventId, Document.class, "events").get("stats", Document.class);
        assertEquals(1, stats.getInteger("totalAccepted"));
    }

    @Test
    void uniqueIndexesCanBeCreatedAfterTheMigration() {
        migration.run();
        rsvpAdapter.ensureIndexes();
        invitationAdapter.ensureIndexes();

        String id = eventId.toHexString();
        assertThrows(DuplicateKeyException.class,
                () -> mongoTemplate.insert(rsvp(id, "u1", "MAYBE", date("2026-10-31T10:00:00Z")), "rsvps"));
        assertThrows(DuplicateKeyException.class, () -> mongoTemplate.insert(
                new Document("eventId", id).append("inviteeId", "u2").append("status", "PENDING"), "invitations"));
    }

    private Document rsvp(String id, String userId) {
        return mongoTemplate.findOne(new Query(Criteria.where("eventId").is(id).and("userId").is(userId)),
                Document.class, "rsvps");
    }

    private static Document rsvp(String eventId, String userId, String status, Object respondedAt) {
        return new Document("eventId", eventId).append("userId", userId).append("status", status)
                .append("respondedAt", respondedAt);
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }
}
