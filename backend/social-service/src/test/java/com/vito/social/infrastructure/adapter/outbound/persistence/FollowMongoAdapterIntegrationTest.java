package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T003 (Ola 2a): el adaptador del grafo contra un MongoDB real. Es la base de las pruebas de
 * concurrencia (T036) y paginación (T049). Con T039 el follow es {@code insertIfAbsent} y el
 * unfollow {@code deleteIfPresent}, idempotentes gracias al índice único del par.
 */
@DataMongoTest
@Import(FollowMongoAdapter.class)
class FollowMongoAdapterIntegrationTest extends MongoContainerTest {

    private static final String COLLECTION = "follow_relationships";

    @Autowired
    private FollowMongoAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection(COLLECTION);
        adapter.ensureIndexes();
    }

    @Test
    void savedRelationshipIsFoundAndCountedFromBothSides() {
        assertTrue(adapter.insertIfAbsent(FollowRelationship.create("ana", "jose")));
        assertTrue(adapter.insertIfAbsent(FollowRelationship.create("caro", "jose")));

        assertTrue(adapter.existsByFollowerIdAndFollowingId("ana", "jose"));
        assertFalse(adapter.existsByFollowerIdAndFollowingId("jose", "ana"));
        assertEquals(2, adapter.countByFollowingId("jose"));
        assertEquals(1, adapter.countByFollowerId("ana"));
        assertEquals(List.of("ana", "caro"),
                adapter.findFollowerIds("jose", 0, 20).stream().sorted().toList());
    }

    @Test
    void deleteRemovesOnlyThatRelationship() {
        adapter.insertIfAbsent(FollowRelationship.create("ana", "jose"));
        adapter.insertIfAbsent(FollowRelationship.create("caro", "jose"));

        assertTrue(adapter.deleteIfPresent("ana", "jose"));

        assertFalse(adapter.existsByFollowerIdAndFollowingId("ana", "jose"));
        assertTrue(adapter.existsByFollowerIdAndFollowingId("caro", "jose"));
        assertEquals(1, mongoTemplate.getCollection(COLLECTION).countDocuments());
    }

    /** T039 (research R5): repetir el follow no es error ni crea otra relación. */
    @Test
    void insertingTheSamePairTwiceKeepsOneRelationship() {
        assertTrue(adapter.insertIfAbsent(FollowRelationship.create("ana", "jose")));
        assertFalse(adapter.insertIfAbsent(FollowRelationship.create("ana", "jose")));

        assertEquals(1, mongoTemplate.getCollection(COLLECTION).countDocuments());
    }

    /** T039 (research R5): dejar de seguir a quien no se sigue no es error ni borra otra cosa. */
    @Test
    void deletingAMissingPairChangesNothing() {
        adapter.insertIfAbsent(FollowRelationship.create("caro", "jose"));

        assertFalse(adapter.deleteIfPresent("ana", "jose"));
        assertEquals(1, mongoTemplate.getCollection(COLLECTION).countDocuments());
    }
}
