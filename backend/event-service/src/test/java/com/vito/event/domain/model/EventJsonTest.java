package com.vito.event.domain.model;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El JSON de settings usa los nombres de ADR-001 / CONVENTIONS §6. */
class EventJsonTest {

    // Mismo ajuste que application.yml: null en un primitivo boolean vale false (default de contrato)
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

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
