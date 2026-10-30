package com.vito.event.infrastructure.adapter.inbound.rest.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.port.inbound.EventService.CreateEventCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-B12: el ObjectMapper de Spring usa {@code settings.isPublic} como el contrato (ADR-001). */
@JsonTest
class EventSettingsJsonMixinTest {

    /** Fixtures de T-C01; Maven ejecuta los tests desde backend/event-service. */
    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");

    @Autowired
    ObjectMapper mapper;

    @Test
    void requestIsPublicIsReadNotIgnored() throws Exception {
        CreateEventCommand command = mapper.readValue(
                "{\"title\":\"x\",\"settings\":{\"isPublic\":true}}", CreateEventCommand.class);

        assertTrue(command.getSettings().isPublic());
    }

    @Test
    void responseUsesIsPublicAndNotPublic() throws Exception {
        JsonNode settings = mapper.valueToTree(Event.EventSettings.builder().isPublic(true).build());

        assertTrue(settings.get("isPublic").asBoolean());
        assertFalse(settings.has("public"));
    }

    @Test
    void settingsRoundTripMatchesCreateEventFixture() throws Exception {
        JsonNode fixtureSettings = mapper.readTree(Files.readString(FIXTURES.resolve("createEvent.201.json")))
                .get("settings");

        Event.EventSettings settings = mapper.treeToValue(fixtureSettings, Event.EventSettings.class);
        JsonNode serialized = mapper.valueToTree(settings);

        assertEquals(fieldNames(fixtureSettings), fieldNames(serialized));
        assertEquals(fixtureSettings, serialized);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
