package com.vito.event.infrastructure.config;

import com.vito.event.infrastructure.adapter.inbound.rest.GlobalExceptionHandler;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

/** T067 (Principio VI, R4): el token del enlace no llega a las trazas ni a los logs de error. */
@ExtendWith(OutputCaptureExtension.class)
class InviteLinkTokenRedactionTest {

    private static final String TOKEN = "k3J9xQ2mV7pL1sT8wY4zBc";

    private final ObservationFilter filter = new InviteLinkTokenRedaction().inviteLinkTokenObservationFilter();

    @Test
    void theTokenIsReplacedByTheTemplate() {
        assertEquals("/api/v1/invite-links/{token}", InviteLinkTokenRedaction.redact("/api/v1/invite-links/" + TOKEN));
        assertEquals("POST http://event-service:8083/api/v1/invite-links/{token}/accept?x=1",
                InviteLinkTokenRedaction.redact("POST http://event-service:8083/api/v1/invite-links/" + TOKEN + "/accept?x=1"));
        assertEquals("/api/v1/events/evt_1/invite-links/inl_1",
                InviteLinkTokenRedaction.redact("/api/v1/events/evt_1/invite-links/inl_1"),
                "la ruta de gestión lleva el linkId, no el token");
        assertNull(InviteLinkTokenRedaction.redact(null));
    }

    @Test
    void spanAttributesNeverCarryTheToken() {
        Observation.Context context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "/api/v1/invite-links/" + TOKEN + "/accept"));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/v1/invite-links/{token}/accept"));
        context.addLowCardinalityKeyValue(KeyValue.of("method", "POST"));
        context.setContextualName("http post /api/v1/invite-links/" + TOKEN + "/accept");

        Observation.Context filtered = filter.map(context);

        assertEquals("/api/v1/invite-links/{token}/accept", filtered.getHighCardinalityKeyValue("http.url").getValue());
        assertEquals("POST", filtered.getLowCardinalityKeyValue("method").getValue());
        assertFalse(filtered.getContextualName().contains(TOKEN));
        filtered.getAllKeyValues().forEach(kv -> assertFalse(kv.getValue().contains(TOKEN), kv.getKey()));
    }

    @Test
    void anUnexpectedErrorWithThePathDoesNotLogTheToken(CapturedOutput output) {
        new GlobalExceptionHandler().handleGenericException(
                new IllegalStateException("fallo al procesar /api/v1/invite-links/" + TOKEN + "/accept"));

        assertTrue(output.getAll().contains("/api/v1/invite-links/{token}/accept"));
        assertFalse(output.getAll().contains(TOKEN));
    }

    @Test
    void anUnknownRouteUnderInviteLinksIsNotLogged(CapturedOutput output) throws Exception {
        new GlobalExceptionHandler().handleGenericException(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/invite-links/" + TOKEN + "/otra", "api/v1/invite-links/" + TOKEN + "/otra"));

        assertFalse(output.getAll().contains(TOKEN));
    }
}
