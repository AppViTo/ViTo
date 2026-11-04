package com.vito.event.domain.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T027 (R7): el JSON de {@code stats} de cada vista coincide con los fixtures de getEvent. */
class EventStatsViewContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static Event fixtureEvent() {
        return Event.builder()
                .organizerId("usr_01H8X2K4QJ")
                .stats(Event.EventStats.builder()
                        .totalInvited(120).totalAccepted(96).totalDeclined(4).totalMaybe(10)
                        .totalWaitlisted(3).totalCheckedIn(80).totalViewers(0).build())
                .build();
    }

    private JsonNode fixtureStats(String file) {
        return mapper.readTree(FIXTURES.resolve(file).toFile()).get("stats");
    }

    @Test
    void organizerViewMatchesTheOrganizerFixture() {
        assertEquals(fixtureStats("getEvent.organizer.200.json"),
                mapper.valueToTree(fixtureEvent()).get("stats"));
    }

    @Test
    void guestViewMatchesTheGuestFixture() {
        assertEquals(fixtureStats("getEvent.guest.200.json"),
                mapper.valueToTree(fixtureEvent().withAcceptedCountOnly()).get("stats"));
    }
}
