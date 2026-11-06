package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch.Change;
import com.vito.event.domain.model.EventPatch;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // ---- applyPatch (US2): $set/$unset condicional, sin tocar stats

    private Event editable(String id, int maxAttendees, int accepted) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        return adapter.save(Event.builder().id(id).organizerId("usr_org").title("Boda").description("Ceremonia")
                .status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(8, ChronoUnit.HOURS))
                        .location("Hacienda").address("Km 5").tags(List.of("boda")).build())
                .settings(Event.EventSettings.builder().isPublic(true).enableMosaic(true).maxAttendees(maxAttendees).build())
                .stats(Event.EventStats.builder().totalAccepted(accepted).totalInvited(120).totalWaitlisted(3).build())
                .build());
    }

    @Test
    void applyPatchChangesOnlyTheSentFieldsAndKeepsStats() {
        editable("evt_p1", 150, 96);
        // Un RSVP que suma entre la lectura y la edición: la edición no lo pisa
        adapter.tryReserveSeat("evt_p1");
        Instant now = Instant.now();

        Event updated = adapter.applyPatch("evt_p1", EventPatch.builder()
                .title(Change.of("Boda — ceremonia y fiesta"))
                .description(Change.of(null))
                .location(Change.of("Hacienda El Retiro"))
                .maxAttendees(Change.of(160))
                .build(), "Alex Villa", now).orElseThrow();

        assertEquals("Boda — ceremonia y fiesta", updated.getTitle());
        assertNull(updated.getDescription());
        assertEquals("Hacienda El Retiro", updated.getDetails().getLocation());
        assertEquals("Km 5", updated.getDetails().getAddress());
        assertEquals(List.of("boda"), updated.getDetails().getTags());
        assertEquals(160, updated.getSettings().getMaxAttendees());
        assertTrue(updated.getSettings().isPublic());
        assertTrue(updated.getSettings().isEnableMosaic());
        assertEquals(97, updated.getStats().getTotalAccepted());
        assertEquals(120, updated.getStats().getTotalInvited());
        assertEquals("Alex Villa", updated.getOrganizerDisplayName());
        assertEquals(now.truncatedTo(ChronoUnit.MILLIS), updated.getUpdatedAt());
        assertEquals("Boda — ceremonia y fiesta", adapter.findById("evt_p1").orElseThrow().getTitle());
    }

    @Test
    void applyPatchWithoutNameKeepsTheStoredOne() {
        Event event = editable("evt_p2", 10, 0);
        event.setOrganizerDisplayName("Alex");
        adapter.save(event);

        Event updated = adapter.applyPatch("evt_p2", EventPatch.builder().title(Change.of("Otro título")).build(),
                null, Instant.now()).orElseThrow();

        assertEquals("Alex", updated.getOrganizerDisplayName());
    }

    @Test
    void applyPatchRefusesACapacityBelowTheAccepted() {
        editable("evt_p3", 10, 5);

        assertEquals(Optional.empty(), adapter.applyPatch("evt_p3",
                EventPatch.builder().maxAttendees(Change.of(4)).build(), null, Instant.now()));
        assertEquals(10, adapter.findById("evt_p3").orElseThrow().getSettings().getMaxAttendees());

        assertEquals(5, adapter.applyPatch("evt_p3", EventPatch.builder().maxAttendees(Change.of(5)).build(),
                null, Instant.now()).orElseThrow().getSettings().getMaxAttendees());
        assertEquals(0, adapter.applyPatch("evt_p3", EventPatch.builder().maxAttendees(Change.of(0)).build(),
                null, Instant.now()).orElseThrow().getSettings().getMaxAttendees());
    }

    @Test
    void applyPatchRefusesCancelledCompletedAndEndedEvents() {
        EventPatch patch = EventPatch.builder().title(Change.of("Otro título")).build();
        for (Event.EventStatus status : new Event.EventStatus[]{Event.EventStatus.CANCELLED, Event.EventStatus.COMPLETED}) {
            Event event = editable("evt_" + status, 10, 0);
            event.setStatus(status);
            adapter.save(event);
            assertEquals(Optional.empty(), adapter.applyPatch(event.getId(), patch, null, Instant.now()), status.name());
        }
        Event ended = editable("evt_ended", 10, 0);
        assertEquals(Optional.empty(), adapter.applyPatch("evt_ended", patch, null,
                ended.getDetails().getEndDate().plusSeconds(1)));
        assertEquals("Boda", adapter.findById("evt_ended").orElseThrow().getTitle());
    }

    // ---- cancel (US5): $set condicional, sin tocar stats; sale de upcoming e invited

    @Test
    void cancelSetsCancelledOnceAndKeepsStats() {
        editable("evt_c1", 100, 40);
        Instant now = Instant.now();

        assertTrue(adapter.cancel("evt_c1", now));
        assertEquals(false, adapter.cancel("evt_c1", now), "la segunda no modifica nada");

        Event cancelled = adapter.findById("evt_c1").orElseThrow();
        assertEquals(Event.EventStatus.CANCELLED, cancelled.getStatus());
        assertEquals(40, cancelled.getStats().getTotalAccepted());
        assertEquals(120, cancelled.getStats().getTotalInvited());
        assertEquals("Boda", cancelled.getTitle());
    }

    @Test
    void cancelRefusesCompletedAndEndedEvents() {
        Event completed = editable("evt_c2", 10, 0);
        completed.setStatus(Event.EventStatus.COMPLETED);
        adapter.save(completed);
        assertEquals(false, adapter.cancel("evt_c2", Instant.now()));

        Event ended = editable("evt_c3", 10, 0);
        assertEquals(false, adapter.cancel("evt_c3", ended.getDetails().getEndDate().plusSeconds(1)));
        assertEquals(Event.EventStatus.PUBLISHED, adapter.findById("evt_c3").orElseThrow().getStatus());
    }

    @Test
    void aCancelledEventLeavesUpcomingAndInvited() {
        editable("evt_c4", 10, 0);
        editable("evt_c5", 10, 0);
        adapter.cancel("evt_c4", Instant.now());

        List<String> upcoming = adapter.findUpcomingEvents(0, 20).stream().map(Event::getId).toList();
        List<String> invited = adapter.findActiveByIds(List.of("evt_c4", "evt_c5"), Instant.now(), 0, 20)
                .stream().map(Event::getId).toList();

        assertEquals(List.of("evt_c5"), upcoming);
        assertEquals(List.of("evt_c5"), invited);
    }
}
