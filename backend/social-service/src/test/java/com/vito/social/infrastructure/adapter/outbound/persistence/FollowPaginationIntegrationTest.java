package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T049 (Ola 2a, FR-013, SC-005): seguidores y seguidos paginados en la base, del más reciente al
 * más antiguo, con {@code _id} de desempate (research R7). Muchas relaciones comparten
 * {@code createdAt}: sin desempate estable, una fila podría salir en dos páginas o en ninguna.
 */
@DataMongoTest
@Import(FollowMongoAdapter.class)
class FollowPaginationIntegrationTest extends MongoContainerTest {

    private static final Instant BASE = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired
    private FollowMongoAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanAndIndex() {
        mongoTemplate.dropCollection(FollowMongoAdapter.COLLECTION);
        adapter.ensureIndexes();
    }

    @Test
    void fiveHundredFollowersWithRepeatedDatesArePagedWithoutRepeatsOrGaps() {
        // 500 seguidores en 50 instantes distintos: 10 relaciones por cada createdAt
        for (int i = 0; i < 500; i++) {
            follow(String.format("usr_%03d", i), "jose", BASE.plusSeconds(i / 10));
        }

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 10; page++) {
            List<String> ids = adapter.findFollowerIds("jose", page, 50);
            assertEquals(50, ids.size(), "página " + page);
            seen.addAll(ids);
        }

        Set<String> unique = new HashSet<>(seen);
        assertEquals(500, unique.size(), "cada seguidor sale exactamente una vez");
        assertEquals(500, seen.size());
        assertTrue(adapter.findFollowerIds("jose", 10, 50).isEmpty(), "más allá del final: vacía");

        // Del más reciente al más antiguo: los del último instante (usr_490..usr_499) van primero
        List<Instant> dates = seen.stream().map(id -> createdAtOf(id, "jose")).toList();
        for (int i = 1; i < dates.size(); i++) {
            assertFalse(dates.get(i).isAfter(dates.get(i - 1)), "orden descendente en la posición " + i);
        }
        assertTrue(seen.subList(0, 10).stream().allMatch(id -> id.compareTo("usr_490") >= 0));
    }

    @Test
    void followingIsPagedFromTheOtherSideOfTheRelationship() {
        follow("ana", "jose", BASE);
        follow("ana", "caro", BASE.plusSeconds(1));
        follow("ana", "luis", BASE.plusSeconds(2));
        follow("caro", "ana", BASE.plusSeconds(3));

        assertEquals(List.of("luis", "caro"), adapter.findFollowingIds("ana", 0, 2));
        assertEquals(List.of("jose"), adapter.findFollowingIds("ana", 1, 2));
        assertEquals(List.of("caro"), adapter.findFollowerIds("ana", 0, 20));
        assertTrue(adapter.findFollowingIds("nadie", 0, 20).isEmpty());
    }

    private void follow(String follower, String following, Instant at) {
        adapter.insertIfAbsent(FollowRelationship.builder()
                .followerId(follower)
                .followingId(following)
                .createdAt(at)
                .notificationsEnabled(true)
                .build());
    }

    private Instant createdAtOf(String follower, String following) {
        Query pair = Query.query(Criteria.where("followerId").is(follower).and("followingId").is(following));
        return mongoTemplate.findOne(pair, FollowRelationship.class, FollowMongoAdapter.COLLECTION).getCreatedAt();
    }
}
