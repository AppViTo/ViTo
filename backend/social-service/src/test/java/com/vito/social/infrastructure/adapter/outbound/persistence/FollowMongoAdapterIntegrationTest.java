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
 * concurrencia (T036) y paginación (T049); cuando T039 y T051 cambien el adaptador, estas
 * pruebas se ajustan a la nueva interfaz.
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
    }

    @Test
    void savedRelationshipIsFoundAndCountedFromBothSides() {
        adapter.save(FollowRelationship.create("ana", "jose"));
        adapter.save(FollowRelationship.create("caro", "jose"));

        assertTrue(adapter.existsByFollowerIdAndFollowingId("ana", "jose"));
        assertFalse(adapter.existsByFollowerIdAndFollowingId("jose", "ana"));
        assertEquals(2, adapter.countByFollowingId("jose"));
        assertEquals(1, adapter.countByFollowerId("ana"));
        assertEquals(List.of("ana", "caro"),
                adapter.findFollowerIdsByFollowingId("jose").stream().sorted().toList());
    }

    @Test
    void deleteRemovesOnlyThatRelationship() {
        adapter.save(FollowRelationship.create("ana", "jose"));
        adapter.save(FollowRelationship.create("caro", "jose"));

        adapter.delete(FollowRelationship.builder().followerId("ana").followingId("jose").build());

        assertFalse(adapter.existsByFollowerIdAndFollowingId("ana", "jose"));
        assertTrue(adapter.existsByFollowerIdAndFollowingId("caro", "jose"));
        assertEquals(1, mongoTemplate.getCollection(COLLECTION).countDocuments());
    }
}
