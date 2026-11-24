package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T036 (Ola 2a, FR-009, FR-011, SC-004): follow y unfollow con peticiones simultáneas contra un
 * MongoDB real. El índice único hace atómico el follow (research R5) y los contadores son
 * {@code count} sobre las relaciones (R6), así que nunca se desvían ni son negativos. Con el
 * {@code exists} + {@code save} anterior, los 50 hilos dejaban varias relaciones del mismo par.
 */
@DataMongoTest
@Import(FollowMongoAdapter.class)
class FollowConcurrencyIntegrationTest extends MongoContainerTest {

    private static final int THREADS = 16;

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
    void fiftySimultaneousFollowsOfTheSamePairLeaveOneRelationship() throws Exception {
        List<Callable<Boolean>> follows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            follows.add(() -> adapter.insertIfAbsent(FollowRelationship.create("ana", "jose")));
        }

        List<Boolean> inserted = runTogether(follows);

        assertEquals(1, inserted.stream().filter(Boolean::booleanValue).count(),
                "solo una petición crea la relación; las demás ven que ya existía");
        assertEquals(1, adapter.countByFollowingId("jose"));
        assertEquals(1, adapter.countByFollowerId("ana"));
    }

    @Test
    void mixedFollowsAndUnfollowsKeepCountersEqualToRelationships() throws Exception {
        List<String> followers = List.of("ana", "caro", "luis", "marta", "pedro");
        List<String> targets = List.of("jose", "sofia");
        AtomicInteger netChanges = new AtomicInteger();
        Random random = new Random(42);

        List<Callable<Boolean>> operations = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            String follower = followers.get(random.nextInt(followers.size()));
            String target = targets.get(random.nextInt(targets.size()));
            if (random.nextBoolean()) {
                operations.add(() -> {
                    boolean changed = adapter.insertIfAbsent(FollowRelationship.create(follower, target));
                    if (changed) netChanges.incrementAndGet();
                    return changed;
                });
            } else {
                operations.add(() -> {
                    boolean changed = adapter.deleteIfPresent(follower, target);
                    if (changed) netChanges.decrementAndGet();
                    return changed;
                });
            }
        }

        runTogether(operations);

        long relationships = mongoTemplate.getCollection(FollowMongoAdapter.COLLECTION).countDocuments();
        assertEquals(netChanges.get(), relationships,
                "cada cambio que el adaptador dijo haber hecho se refleja en la colección");
        long followersCounted = 0;
        for (String target : targets) {
            long count = adapter.countByFollowingId(target);
            assertTrue(count >= 0 && count <= followers.size(), "seguidores de " + target + ": " + count);
            followersCounted += count;
        }
        long followingCounted = 0;
        for (String follower : followers) {
            long count = adapter.countByFollowerId(follower);
            assertTrue(count >= 0 && count <= targets.size(), "seguidos de " + follower + ": " + count);
            followingCounted += count;
        }
        assertEquals(relationships, followersCounted);
        assertEquals(relationships, followingCounted);
    }

    /** Lanza todas las tareas a la vez (mismo punto de partida) y espera sus resultados. */
    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
