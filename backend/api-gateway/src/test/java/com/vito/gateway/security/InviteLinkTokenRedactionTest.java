package com.vito.gateway.security;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** T067 (Principio VI, R4): el token del enlace no llega a las trazas del gateway. */
class InviteLinkTokenRedactionTest {

    private static final String TOKEN = "k3J9xQ2mV7pL1sT8wY4zBc";

    private final ObservationFilter filter = new InviteLinkTokenRedaction().inviteLinkTokenObservationFilter();

    @Test
    void theTokenIsReplacedByTheTemplate() {
        assertEquals("/api/v1/invite-links/{token}", InviteLinkTokenRedaction.redact("/api/v1/invite-links/" + TOKEN));
        assertEquals("http://event-service:8083/api/v1/invite-links/{token}/accept?x=1",
                InviteLinkTokenRedaction.redact("http://event-service:8083/api/v1/invite-links/" + TOKEN + "/accept?x=1"));
        assertEquals("/api/v1/events/evt_1/invite-links/inl_1",
                InviteLinkTokenRedaction.redact("/api/v1/events/evt_1/invite-links/inl_1"));
        assertNull(InviteLinkTokenRedaction.redact(null));
    }

    @Test
    void serverAndClientSpansNeverCarryTheToken() {
        Observation.Context context = new Observation.Context();
        // http.server.requests y el cliente HTTP del gateway hacia event-service
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "/api/v1/invite-links/" + TOKEN));
        context.addHighCardinalityKeyValue(KeyValue.of("http.uri", "http://event-service:8083/api/v1/invite-links/" + TOKEN + "/accept"));
        context.addLowCardinalityKeyValue(KeyValue.of("method", "GET"));
        context.setContextualName("HTTP GET /api/v1/invite-links/" + TOKEN);

        Observation.Context filtered = filter.map(context);

        filtered.getAllKeyValues().forEach(kv -> assertFalse(kv.getValue().contains(TOKEN), kv.getKey()));
        assertFalse(filtered.getContextualName().contains(TOKEN));
        assertEquals("GET", filtered.getLowCardinalityKeyValue("method").getValue());
    }
}
