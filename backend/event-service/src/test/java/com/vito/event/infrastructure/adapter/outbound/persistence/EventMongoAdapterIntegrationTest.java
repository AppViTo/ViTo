package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Event;
import com.vito.event.support.MongoContainerTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** T003: el adaptador contra un MongoDB real (los tests con mock no ven el filtro aplicado). */
@DataMongoTest
@Import(EventMongoAdapter.class)
class EventMongoAdapterIntegrationTest extends MongoContainerTest {

    @Autowired
    private EventMongoAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection("events");
    }

    @Test
    void startDateIsStoredAsUtcDateAndReadBackUnchanged() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Event saved = adapter.save(event("org-1", true, start));

        // Bug-B01: un instante (BSON Date), no texto con hora local
        Document raw = mongoTemplate.getCollection("events").find().first();
        Object stored = raw.get("details", Document.class).get("startDate");
        assertInstanceOf(Date.class, stored);
        assertEquals(start, ((Date) stored).toInstant());
        assertEquals(start, adapter.findById(saved.getId()).orElseThrow().getDetails().getStartDate());
    }

    @Test
    void findUpcomingEventsReturnsOnlyPublicFuturePublishedEvents() {
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        Event visible = adapter.save(event("org-1", true, future));
        adapter.save(event("org-2", false, future));
        adapter.save(event("org-3", true, Instant.now().minus(1, ChronoUnit.DAYS)));

        List<Event> upcoming = adapter.findUpcomingEvents(0, 20);

        assertEquals(List.of(visible.getId()), upcoming.stream().map(Event::getId).toList());
    }

    private static Event event(String organizerId, boolean isPublic, Instant start) {
        return Event.builder()
                .organizerId(organizerId)
                .title("Evento de prueba")
                .status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder()
                        .startDate(start)
                        .endDate(start.plus(4, ChronoUnit.HOURS))
                        .location("Salón")
                        .build())
                .settings(Event.EventSettings.builder().isPublic(isPublic).build())
                .createdAt(Instant.now())
                .build();
    }
}
