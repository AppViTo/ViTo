package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.InviteLink;
import com.vito.event.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** T035 (R4): el {@code $inc} condicional de {@code uses} nunca supera {@code maxUses}. */
@DataMongoTest
@Import(InviteLinkMongoAdapter.class)
class InviteLinkMongoAdapterIntegrationTest extends MongoContainerTest {

    private static final int PEOPLE = 20;

    @Autowired
    private InviteLinkMongoAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private int tokens;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection(InviteLinkMongoAdapter.COLLECTION);
        adapter.ensureIndexes();
    }

    private InviteLink saved(Integer maxUses, boolean revoked, Instant expiresAt, Instant createdAt) {
        String token = String.format("%022d", ++tokens);
        return adapter.save(InviteLink.builder().eventId("evt_1").token(token).createdBy("usr_org")
                .expiresAt(expiresAt).maxUses(maxUses).uses(0).revoked(revoked).createdAt(createdAt).build());
    }

    private InviteLink saved(Integer maxUses) {
        return saved(maxUses, false, now.plus(7, ChronoUnit.DAYS), now);
    }

    @RepeatedTest(5)
    void twentySimultaneousJoinsNeverExceedMaxUses() throws Exception {
        String id = saved(5).getId();

        ExecutorService pool = Executors.newFixedThreadPool(PEOPLE);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < PEOPLE; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return adapter.tryUse(id, now);
                }));
            }
            start.countDown();
            int used = 0;
            for (Future<Boolean> result : results) {
                used += result.get() ? 1 : 0;
            }
            assertEquals(5, used);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, adapter.findById(id).orElseThrow().getUses());
    }

    @Test
    void withoutLimitEveryJoinCounts() {
        String id = saved(null).getId();

        for (int i = 0; i < 30; i++) {
            assertTrue(adapter.tryUse(id, now));
        }
        assertEquals(30, adapter.findById(id).orElseThrow().getUses());
    }

    @Test
    void revokedOrExpiredLinksAreNotUsed() {
        String revoked = saved(null, true, now.plus(1, ChronoUnit.DAYS), now).getId();
        String expired = saved(null, false, now.minusSeconds(1), now).getId();

        assertFalse(adapter.tryUse(revoked, now));
        assertFalse(adapter.tryUse(expired, now));
    }

    @Test
    void releaseUseGivesBackOneAndNeverGoesNegative() {
        String id = saved(1).getId();
        assertTrue(adapter.tryUse(id, now));
        assertFalse(adapter.tryUse(id, now));

        adapter.releaseUse(id);
        adapter.releaseUse(id);

        assertEquals(0, adapter.findById(id).orElseThrow().getUses());
        assertTrue(adapter.tryUse(id, now));
    }

    @Test
    void revokeIsIdempotent() {
        String id = saved(null).getId();

        adapter.revoke(id);
        adapter.revoke(id);

        assertTrue(adapter.findById(id).orElseThrow().isRevoked());
    }

    @Test
    void theTokenIsUnique() {
        InviteLink first = saved(null);

        assertThrows(DuplicateKeyException.class, () -> adapter.save(first.toBuilder().id(null).build()));
        assertEquals(first.getId(), adapter.findByToken(first.getToken()).orElseThrow().getId());
    }

    /** FR-008: los vigentes primero (los más nuevos antes) y después revocados, vencidos y agotados. */
    @Test
    void usableLinksAreListedFirst() {
        InviteLink oldUsable = saved(null, false, now.plus(3, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS));
        InviteLink revoked = saved(null, true, now.plus(3, ChronoUnit.DAYS), now);
        InviteLink exhausted = saved(1, false, now.plus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));
        adapter.tryUse(exhausted.getId(), now);
        InviteLink expired = saved(null, false, now.minusSeconds(5), now.minus(1, ChronoUnit.DAYS));
        InviteLink newUsable = saved(10, false, now.plus(3, ChronoUnit.DAYS), now.minusSeconds(1));

        List<String> ids = adapter.findByEventId("evt_1", now, 0, 20).stream().map(InviteLink::getId).toList();

        assertEquals(List.of(newUsable.getId(), oldUsable.getId()), ids.subList(0, 2));
        assertEquals(5, ids.size());
        assertTrue(ids.subList(2, 5).containsAll(List.of(revoked.getId(), exhausted.getId(), expired.getId())));
        assertEquals(2, adapter.findByEventId("evt_1", now, 1, 3).size());
    }
}
