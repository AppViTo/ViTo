package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.application.service.EventServiceImpl;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.model.EventPatch.Change;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import com.vito.event.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T046 (US2): {@code PATCH /api/v1/events/{eventId}} responde como los fixtures
 * {@code updateEvent.*}. Controlador, servicio, handler de errores y ObjectMapper reales.
 * Las fechas del fixture ya pasaron (un evento terminado no se edita): el evento guardado usa
 * fechas futuras y la respuesta se compara con el fixture con esas mismas fechas.
 */
@JsonTest
class UpdateEventContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");
    private static final Pattern UTC_TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private static final String EVENT = "evt_01H9ZP5RKM";
    private static final String ORGANIZER = "usr_01H8X2K4QJ";

    @Autowired
    JsonMapper mapper;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private MockMvc mvc;
    private Event stored;
    private Instant start;

    @BeforeEach
    void setUp() throws Exception {
        EventServiceImpl service = new EventServiceImpl(eventRepository, mock(RSVPRepository.class),
                mock(InvitationRepository.class), mock(EventPublisherPort.class), mock(QRCodeGenerator.class),
                mock(AttendanceService.class));
        mvc = MockMvcBuilders.standaloneSetup(new EventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();

        start = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        // Antes de la edición: el título, la descripción y el cupo viejos
        stored = futureDates(mapper.treeToValue(fixture("updateEvent.200.json"), Event.class));
        stored.setTitle("Boda Andrea & Luis");
        stored.setDescription("Ceremonia en la hacienda");
        stored.getSettings().setMaxAttendees(150);
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(stored));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Event futureDates(Event event) {
        event.getDetails().setStartDate(start);
        event.getDetails().setEndDate(start.plus(8, ChronoUnit.HOURS));
        return event;
    }

    // ---- 200

    @Test
    void theFixtureRequestIsAPartialPatchAndTheResponseIsThe200Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        Event afterPatch = futureDates(mapper.treeToValue(fixture("updateEvent.200.json"), Event.class));
        when(eventRepository.applyPatch(eq(EVENT), any(), any(), any())).thenReturn(Optional.of(afterPatch));

        JsonNode body = perform(patch("/api/v1/events/" + EVENT).contentType(MERGE_PATCH)
                .content(fixtureText("updateEvent.request.json")), 200);

        ObjectNode expected = (ObjectNode) fixture("updateEvent.200.json").deepCopy();
        ((ObjectNode) expected.get("details")).put("startDate", start.toString());
        ((ObjectNode) expected.get("details")).put("endDate", start.plus(8, ChronoUnit.HOURS).toString());
        assertEquals(expected, body);

        // Solo los tres campos del request: nada de details ni del resto de settings
        ArgumentCaptor<EventPatch> patch = ArgumentCaptor.forClass(EventPatch.class);
        verify(eventRepository).applyPatch(eq(EVENT), patch.capture(), eq("Alex Villa"), any());
        assertEquals(EventPatch.builder()
                .title(Change.of("Boda Andrea & Luis — ceremonia y fiesta"))
                .description(Change.of("Ceremonia en la hacienda, fiesta hasta el amanecer"))
                .maxAttendees(Change.of(160))
                .build(), patch.getValue());
    }

    @Test
    void putIsNoLongerAccepted() throws Exception {
        authenticateAs(ORGANIZER);
        MvcResult result = mvc.perform(put("/api/v1/events/" + EVENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Otro título\"}")).andReturn();

        assertEquals(405, result.getResponse().getStatus());
    }

    // ---- errores

    @Test
    void notEditableFieldIsThe400Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        assertErrorMatchesFixture("updateEvent.400.json", perform(patch("/api/v1/events/" + EVENT)
                .contentType(MERGE_PATCH).content("{\"title\":\"Otro título\",\"status\":\"CANCELLED\"}"), 400));
    }

    @Test
    void otherShapeErrorsAre400() throws Exception {
        authenticateAs(ORGANIZER);
        for (String body : new String[]{"{}", "{\"color\":\"rojo\"}", "{\"details\":{\"venue\":\"x\"}}",
                "{\"details\":null}", "{\"title\":5}", "{\"settings\":{\"maxAttendees\":\"muchos\"}}",
                "{\"details\":{\"startDate\":\"mañana\"}}", "{\"stats\":{\"totalAccepted\":0}}", "[]"}) {
            JsonNode error = perform(patch("/api/v1/events/" + EVENT).contentType(MERGE_PATCH).content(body), 400);
            assertEquals("VALIDATION_ERROR", error.get("error").asString(), body);
        }
    }

    @Test
    void capacityBelowAcceptedIsThe409Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        assertErrorMatchesFixture("updateEvent.capacity.409.json", perform(patch("/api/v1/events/" + EVENT)
                .contentType(MERGE_PATCH).content("{\"settings\":{\"maxAttendees\":90}}"), 409));
    }

    @Test
    void cancelledEventIsTheState409Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        stored.setStatus(Event.EventStatus.CANCELLED);
        assertErrorMatchesFixture("updateEvent.state.409.json", perform(patch("/api/v1/events/" + EVENT)
                .contentType(MERGE_PATCH).content("{\"title\":\"Otro título\"}"), 409));
    }

    @Test
    void otherUserIsThe403Fixture() throws Exception {
        authenticateAs("usr_otro");
        assertErrorMatchesFixture("updateEvent.403.json", perform(patch("/api/v1/events/" + EVENT)
                .contentType(MERGE_PATCH).content("{\"title\":\"Otro título\"}"), 403));
    }

    @Test
    void unknownEventIsThe404Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());
        assertErrorMatchesFixture("updateEvent.404.json", perform(patch("/api/v1/events/evt_x")
                .contentType(MERGE_PATCH).content("{\"title\":\"Otro título\"}"), 404));
    }

    @Test
    void withoutSessionIsThe401Fixture() throws Exception {
        assertErrorMatchesFixture("updateEvent.401.json", perform(patch("/api/v1/events/" + EVENT)
                .contentType(MERGE_PATCH).content("{\"title\":\"Otro título\"}"), 401));
    }

    // ---- helpers

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "Alex Villa"), null, List.of(new SimpleGrantedAuthority("USER"))));
    }

    private JsonNode perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, result.getResponse().getStatus(), content);
        return mapper.readTree(content);
    }

    private String fixtureText(String name) throws Exception {
        return Files.readString(FIXTURES.resolve(name));
    }

    private JsonNode fixture(String name) throws Exception {
        return mapper.readTree(fixtureText(name));
    }

    private void assertErrorMatchesFixture(String name, JsonNode actual) throws Exception {
        ObjectNode expected = (ObjectNode) fixture(name).deepCopy();
        ObjectNode body = (ObjectNode) actual.deepCopy();
        assertTrue(UTC_TIMESTAMP.matcher(body.remove("timestamp").asString()).matches(), name);
        expected.remove("timestamp");
        assertEquals(expected, body, name);
    }
}
