package com.vito.event.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vito.event.application.service.EventServiceImpl;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * T-B07: {@code GET /api/v1/events?scope=} responde como los fixtures de T-C06 (ADR-014).
 * Usa el ObjectMapper de Spring (con el mixin de {@code isPublic}), el controlador, el handler
 * de errores y el servicio reales; solo los repositorios son mocks.
 */
@JsonTest
class ListEventsContractTest {

    /** Maven ejecuta los tests desde backend/event-service. */
    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");

    private static final String ORGANIZER = "usr_01H8X2K4QJ";
    private static final String VIEWER = "usr_01H8X2K4QV";
    private static final Pattern DATE_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    private static final Pattern UTC_TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

    @Autowired
    ObjectMapper mapper;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final RSVPRepository rsvpRepository = mock(RSVPRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EventServiceImpl service = new EventServiceImpl(eventRepository, rsvpRepository,
                mock(InvitationRepository.class), mock(EventPublisherPort.class), mock(QRCodeGenerator.class));
        mvc = MockMvcBuilders.standaloneSetup(new EventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withoutScope_ListsMine_AsFixture() throws Exception {
        authenticateAs(ORGANIZER);
        when(eventRepository.findByOrganizerId(ORGANIZER, 0, 20)).thenReturn(fixtureEvents("listEvents.mine.200.json"));

        assertMatchesFixture("listEvents.mine.200.json", perform(get("/api/v1/events"), 200));
        verify(eventRepository, never()).findUpcomingEvents(anyInt(), anyInt());
    }

    @Test
    void scopeMine_ListsMine_AsFixture() throws Exception {
        authenticateAs(ORGANIZER);
        when(eventRepository.findByOrganizerId(ORGANIZER, 0, 20)).thenReturn(fixtureEvents("listEvents.mine.200.json"));

        assertMatchesFixture("listEvents.mine.200.json", perform(get("/api/v1/events").param("scope", "mine"), 200));
    }

    @Test
    void scopeMine_IgnoresIdentityQueryParams() throws Exception {
        authenticateAs(ORGANIZER);
        when(eventRepository.findByOrganizerId(ORGANIZER, 0, 20)).thenReturn(List.of());

        // ADR-014: la identidad sale solo del usuario autenticado, nunca de la query
        perform(get("/api/v1/events").param("organizerId", "usr_otro").param("userId", "usr_otro"), 200);
        verify(eventRepository).findByOrganizerId(ORGANIZER, 0, 20);
        verify(eventRepository, never()).findByOrganizerId("usr_otro", 0, 20);
    }

    @Test
    void scopeUpcoming_ListsPublicEvents_AsFixture() throws Exception {
        authenticateAs(VIEWER);
        when(eventRepository.findUpcomingEvents(0, 20)).thenReturn(fixtureEvents("listEvents.upcoming.200.json"));
        // El fixture muestra la dirección de evt_01H9D4E5F6 (hideAddressUntilAccepted): quien consulta la aceptó
        when(rsvpRepository.findByEventIdAndUserId("evt_01H9D4E5F6", VIEWER)).thenReturn(Optional.of(
                RSVP.builder().eventId("evt_01H9D4E5F6").userId(VIEWER).status(RSVP.RSVPStatus.ACCEPTED).build()));

        assertMatchesFixture("listEvents.upcoming.200.json",
                perform(get("/api/v1/events").param("scope", "upcoming"), 200));
        verify(eventRepository, never()).findByOrganizerId(any(), anyInt(), anyInt());
    }

    @Test
    void scopeUpcoming_HidesAddressFromViewerWithoutAcceptedRsvp() throws Exception {
        authenticateAs(VIEWER);
        when(eventRepository.findUpcomingEvents(0, 20)).thenReturn(fixtureEvents("listEvents.upcoming.200.json"));
        when(rsvpRepository.findByEventIdAndUserId(any(), any())).thenReturn(Optional.empty());

        JsonNode body = perform(get("/api/v1/events").param("scope", "upcoming"), 200);

        JsonNode visible = body.get(0).get("details");
        JsonNode hidden = body.get(1).get("details");
        assertEquals("Cl. 93 #11-19, Bogotá", visible.get("address").asText());
        assertTrue(isAbsentOrNull(hidden, "address"));
        assertTrue(isAbsentOrNull(hidden, "latitude"));
        assertTrue(isAbsentOrNull(hidden, "longitude"));
        assertEquals("Cerro de Monserrate", hidden.get("location").asText());
    }

    @Test
    void scopeMine_SerializesEventDatesInUtcWithZ() throws Exception {
        authenticateAs(ORGANIZER);
        when(eventRepository.findByOrganizerId(ORGANIZER, 0, 20)).thenReturn(fixtureEvents("listEvents.mine.200.json"));

        JsonNode event = perform(get("/api/v1/events").param("scope", "mine"), 200).get(0);

        // Bug-B01: sin la Z, la app interpretaba la hora UTC como hora local (8 pm en Bogotá → 1 am)
        assertEquals("2026-08-23T01:00:00Z", event.get("details").get("startDate").asText());
        assertEquals("2026-08-23T07:00:00Z", event.get("details").get("endDate").asText());
        assertTrue(event.get("createdAt").asText().endsWith("Z"));
    }

    @Test
    void createEventDate_KeepsTheInstantSentByTheClient() throws Exception {
        // 20:00 en Bogotá (UTC−5), como lo envía la app
        Event.EventDetails details = mapper.readValue(
                "{\"startDate\":\"2026-12-10T01:00:00.000Z\"}", Event.EventDetails.class);

        assertEquals(Instant.parse("2026-12-10T01:00:00Z"), details.getStartDate());
        assertTrue(mapper.writeValueAsString(details).contains("\"startDate\":\"2026-12-10T01:00:00Z\""));
    }

    @Test
    void unknownScope_Is400_AsFixture() throws Exception {
        authenticateAs(ORGANIZER);

        JsonNode body = perform(get("/api/v1/events").param("scope", "all"), 400);

        assertErrorMatchesFixture("listEvents.400.json", body);
    }

    @Test
    void sizeOver100_Is400ValidationError() throws Exception {
        authenticateAs(ORGANIZER);

        JsonNode body = perform(get("/api/v1/events").param("size", "101"), 400);

        assertEquals("VALIDATION_ERROR", body.get("error").asText());
        assertEquals(400, body.get("status").asInt());
        assertEquals("Máximo 100", body.get("details").get("size").asText());
        assertTrue(UTC_TIMESTAMP.matcher(body.get("timestamp").asText()).matches());
    }

    @Test
    void withoutAuthentication_Is401_AsFixture() throws Exception {
        JsonNode body = perform(get("/api/v1/events"), 401);

        assertErrorMatchesFixture("listEvents.401.json", body);
    }

    // ---- helpers ----------------------------------------------------------------------

    private static void authenticateAs(String userId) {
        TestingAuthenticationToken user = new TestingAuthenticationToken(userId, null);
        user.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(user);
    }

    private JsonNode perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(expectedStatus, result.getResponse().getStatus());
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Event> fixtureEvents(String name) throws Exception {
        return mapper.readValue(Files.readString(FIXTURES.resolve(name)), new TypeReference<>() { });
    }

    private JsonNode fixture(String name) throws Exception {
        return mapper.readTree(Files.readString(FIXTURES.resolve(name)));
    }

    /** Mismo JSON que el fixture salvo lo que CONVENTIONS §6 da por equivalente. */
    private void assertMatchesFixture(String name, JsonNode actual) throws Exception {
        assertTrue(actual.isArray(), "listEvents responde un array plano (ADR-002)");
        assertEquals(normalize(fixture(name)), normalize(actual));
    }

    /** Envelope de ADR-003 igual al fixture; {@code timestamp} cambia en cada respuesta. */
    private void assertErrorMatchesFixture(String name, JsonNode actual) throws Exception {
        ObjectNode expected = (ObjectNode) fixture(name).deepCopy();
        ObjectNode body = (ObjectNode) actual.deepCopy();
        assertTrue(UTC_TIMESTAMP.matcher(body.remove("timestamp").asText()).matches());
        expected.remove("timestamp");
        assertEquals(expected, body);
    }

    /**
     * CONVENTIONS §6: un campo en {@code null} equivale a uno ausente, y las fechas se comparan
     * como instantes ({@code .000Z} del fixture y {@code Z} de la respuesta son el mismo).
     */
    private static JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                if (!field.getValue().isNull()) {
                    copy.set(field.getKey(), normalize(field.getValue()));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> copy.add(normalize(item)));
            return copy;
        }
        if (node.isTextual() && DATE_TIME.matcher(node.asText()).matches()) {
            return JsonNodeFactory.instance.textNode(toInstant(node.asText()).toString());
        }
        return node;
    }

    private static Instant toInstant(String value) {
        return OffsetDateTime.parse(value).toInstant();
    }

    private static boolean isAbsentOrNull(JsonNode node, String field) {
        return !node.has(field) || node.get(field).isNull();
    }
}
