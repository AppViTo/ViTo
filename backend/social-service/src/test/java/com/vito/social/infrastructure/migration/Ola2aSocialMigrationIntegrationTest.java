package com.vito.social.infrastructure.migration;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.infrastructure.adapter.outbound.persistence.FollowMongoAdapter;
import com.vito.social.support.MongoContainerTest;
import org.bson.Document;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T013 (Ola 2a, Principio IX): duplicados, relaciones con uno mismo y fechas en texto en
 * {@code follow_relationships}; una sola vez; después se pueden crear los índices de T015.
 */
@DataMongoTest
@Import({Ola2aSocialMigration.class, FollowMongoAdapter.class})
class Ola2aSocialMigrationIntegrationTest extends MongoContainerTest {

    private static final String COLLECTION = FollowMongoAdapter.COLLECTION;

    @Autowired
    private Ola2aSocialMigration migration;

    @Autowired
    private FollowMongoAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void seed() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(Ola2aSocialMigration.MIGRATIONS);

        // ana siguió a jose tres veces (el follow no era idempotente): queda la más antigua
        mongoTemplate.insert(follow("ana", "jose", date("2026-10-02T10:00:00Z")), COLLECTION);
        mongoTemplate.insert(follow("ana", "jose", date("2026-10-01T10:00:00Z")), COLLECTION);
        mongoTemplate.insert(follow("ana", "jose", "2026-10-03T10:00:00"), COLLECTION);
        // caro se siguió a sí misma
        mongoTemplate.insert(follow("caro", "caro", date("2026-10-01T09:00:00Z")), COLLECTION);
        // Fecha guardada como texto con hora local (LocalDateTime serializado)
        mongoTemplate.insert(follow("caro", "jose", "2026-10-04T12:30:00"), COLLECTION);
        // Fecha en texto ilegible: se quita, la relación se conserva
        mongoTemplate.insert(follow("jose", "ana", "ayer"), COLLECTION);
        mongoTemplate.insert(follow("jose", "caro", date("2026-10-05T08:00:00Z")), COLLECTION);
    }

    @Test
    void leavesOneRelationshipPerPairWithoutSelfFollowsAndWithUtcDates() {
        assertTrue(migration.run());

        assertEquals(4, mongoTemplate.getCollection(COLLECTION).countDocuments());
        List<Document> anaJose = find("ana", "jose");
        assertEquals(1, anaJose.size());
        assertEquals(date("2026-10-01T10:00:00Z"), anaJose.get(0).get("createdAt"));
        assertTrue(find("caro", "caro").isEmpty());
        assertEquals(date("2026-10-04T12:30:00Z"), find("caro", "jose").get(0).get("createdAt"));
        assertFalse(find("jose", "ana").get(0).containsKey("createdAt"));

        Document record = mongoTemplate.findById(Ola2aSocialMigration.ID, Document.class,
                Ola2aSocialMigration.MIGRATIONS);
        assertEquals(2, record.getInteger("duplicatesRemoved"));
        assertEquals(1, record.getInteger("selfFollowsRemoved"));
        assertEquals(3, record.getInteger("datesConverted"), "incluye la ilegible y la del duplicado");
    }

    @Test
    void textDateOfTheOldestDuplicateIsComparedAsADate() {
        mongoTemplate.dropCollection(COLLECTION);
        // La más antigua está en texto: sin convertir primero, el orden mezclaría texto y fechas
        mongoTemplate.insert(follow("ana", "jose", date("2026-10-02T10:00:00Z")), COLLECTION);
        mongoTemplate.insert(follow("ana", "jose", "2026-09-30T10:00:00"), COLLECTION);

        migration.run();

        List<Document> anaJose = find("ana", "jose");
        assertEquals(1, anaJose.size());
        assertEquals(date("2026-09-30T10:00:00Z"), anaJose.get(0).get("createdAt"));
    }

    @Test
    void runsOnlyOnce() {
        assertTrue(migration.run());
        mongoTemplate.insert(follow("dani", "dani", date("2026-10-06T08:00:00Z")), COLLECTION);

        assertFalse(migration.run());
        assertEquals(1, find("dani", "dani").size());
    }

    @Test
    void indexesCanBeCreatedAfterTheMigrationAndTheModelReadsTheDates() {
        migration.run();
        adapter.ensureIndexes();

        assertThrows(DuplicateKeyException.class,
                () -> mongoTemplate.insert(follow("ana", "jose", date("2026-10-07T08:00:00Z")), COLLECTION));
        List<String> names = mongoTemplate.indexOps(COLLECTION).getIndexInfo().stream()
                .map(index -> index.getName()).toList();
        assertTrue(names.containsAll(List.of(FollowMongoAdapter.UNIQUE_PAIR_INDEX,
                "followingId_createdAt_id", "followerId_createdAt_id")), names.toString());

        FollowRelationship read = mongoTemplate.findOne(
                new Query(Criteria.where("followerId").is("caro").and("followingId").is("jose")),
                FollowRelationship.class, COLLECTION);
        assertEquals(Instant.parse("2026-10-04T12:30:00Z"), read.getCreatedAt());
    }

    private List<Document> find(String followerId, String followingId) {
        return mongoTemplate.find(new Query(Criteria.where("followerId").is(followerId)
                .and("followingId").is(followingId)), Document.class, COLLECTION);
    }

    private static Document follow(String followerId, String followingId, Object createdAt) {
        return new Document("followerId", followerId).append("followingId", followingId)
                .append("createdAt", createdAt).append("notificationsEnabled", true)
                .append("_class", FollowRelationship.class.getName());
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }
}
