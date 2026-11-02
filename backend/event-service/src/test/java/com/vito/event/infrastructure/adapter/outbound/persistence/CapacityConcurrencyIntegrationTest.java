package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * T022 (R1, SC-003): 50 personas confirman a la vez en un evento de cupo 20. Con el
 * leer-comparar-escribir anterior se confirmaban más de 20; con el {@code $inc} condicional
 * quedan exactamente 20 confirmadas y 30 en lista de espera.
 */
@DataMongoTest
@Import({EventMongoAdapter.class, RSVPMongoAdapter.class, AttendanceService.class})
class CapacityConcurrencyIntegrationTest extends MongoContainerTest {

    private static final int CAPACITY = 20;
    private static final int PEOPLE = 50;

    @Autowired
    private EventMongoAdapter eventAdapter;

    @Autowired
    private RSVPMongoAdapter rsvpAdapter;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private EventPublisherPort eventPublisher;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection("events");
        mongoTemplate.dropCollection("rsvps");
        rsvpAdapter.ensureIndexes();
    }

    @RepeatedTest(10)
    void reserveSeatNeverExceedsTheCapacity() throws Exception {
        String eventId = eventAdapter.save(event(CAPACITY, Event.EventStatus.PUBLISHED)).getId();

        List<Boolean> results = concurrently(i -> eventAdapter.tryReserveSeat(eventId));

        assertEquals(CAPACITY, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(PEOPLE - CAPACITY, results.stream().filter(r -> !r).count());
        assertEquals(CAPACITY, acceptedCounter(eventId));
    }

    @RepeatedTest(10)
    void confirmLeavesExactlyTheCapacityAcceptedAndTheRestWaitlisted() throws Exception {
        String eventId = eventAdapter.save(event(CAPACITY, Event.EventStatus.PUBLISHED)).getId();

        List<RSVP> results = concurrently(i -> attendanceService.confirm(eventId, "user-" + i, "Persona " + i, null));

        assertEquals(CAPACITY, results.stream().filter(r -> r.getStatus() == RSVP.RSVPStatus.ACCEPTED).count());
        assertEquals(PEOPLE - CAPACITY, results.stream().filter(r -> r.getStatus() == RSVP.RSVPStatus.WAITLISTED).count());
        assertEquals(CAPACITY, rsvpAdapter.countByEventIdAndStatus(eventId, RSVP.RSVPStatus.ACCEPTED));
        assertEquals(CAPACITY, acceptedCounter(eventId));
        assertEquals(PEOPLE - CAPACITY, stats(eventId).getTotalWaitlisted());
    }

    @Test
    void releasingASeatPromotesTheOldestWaitlisted() {
        String eventId = eventAdapter.save(event(1, Event.EventStatus.PUBLISHED)).getId();
        attendanceService.confirm(eventId, "first", "Primera", null);
        attendanceService.confirm(eventId, "second", "Segunda", null);
        attendanceService.confirm(eventId, "third", "Tercera", null);

        attendanceService.release(eventId, "first", RSVP.RSVPStatus.DECLINED, "Primera", null);

        assertEquals(RSVP.RSVPStatus.ACCEPTED, status(eventId, "second"));
        assertEquals(RSVP.RSVPStatus.WAITLISTED, status(eventId, "third"));
        Event.EventStats stats = stats(eventId);
        assertEquals(1, stats.getTotalAccepted());
        assertEquals(1, stats.getTotalWaitlisted());
        assertEquals(1, stats.getTotalDeclined());
    }

    @Test
    void unlimitedEventsAlwaysHaveASeat() {
        String eventId = eventAdapter.save(event(0, Event.EventStatus.PUBLISHED)).getId();

        for (int i = 0; i < 30; i++) {
            eventAdapter.tryReserveSeat(eventId);
        }

        assertEquals(30, acceptedCounter(eventId));
    }

    @Test
    void cancelledEventsDoNotTakeConfirmations() {
        String eventId = eventAdapter.save(event(0, Event.EventStatus.CANCELLED)).getId();

        assertFalse(eventAdapter.tryReserveSeat(eventId));
    }

    private <T> List<T> concurrently(IndexedCall<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PEOPLE);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < PEOPLE; i++) {
                int index = i;
                Callable<T> task = () -> {
                    start.await();
                    return call.apply(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int acceptedCounter(String eventId) {
        return stats(eventId).getTotalAccepted();
    }

    private Event.EventStats stats(String eventId) {
        return eventAdapter.findById(eventId).orElseThrow().getStats();
    }

    private RSVP.RSVPStatus status(String eventId, String userId) {
        return mongoTemplate.findOne(new Query(Criteria.where("eventId").is(eventId).and("userId").is(userId)),
                RSVP.class, "rsvps").getStatus();
    }

    private static Event event(int maxAttendees, Event.EventStatus status) {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        return Event.builder()
                .organizerId("org-1")
                .title("Evento con cupo")
                .status(status)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(4, ChronoUnit.HOURS)).build())
                .settings(Event.EventSettings.builder().isPublic(true).maxAttendees(maxAttendees).build())
                .stats(Event.EventStats.builder().build())
                .createdAt(Instant.now())
                .build();
    }

    @FunctionalInterface
    private interface IndexedCall<T> {
        T apply(int index) throws Exception;
    }
}
