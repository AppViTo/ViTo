package com.vito.event.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El JSON de settings usa los nombres de ADR-001 / CONVENTIONS §6. */
class EventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hideAddressUntilAcceptedRoundTripsWithContractName() throws Exception {
        Event.EventSettings settings = mapper.readValue(
                "{\"hideAddressUntilAccepted\":true}", Event.EventSettings.class);
        assertTrue(settings.isHideAddressUntilAccepted());

        JsonNode json = mapper.valueToTree(settings);
        assertTrue(json.get("hideAddressUntilAccepted").asBoolean());
    }

    @Test
    void hideAddressUntilAcceptedDefaultsToFalse() throws Exception {
        assertFalse(mapper.readValue("{}", Event.EventSettings.class).isHideAddressUntilAccepted());
    }
}
