package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.application.service.EventServiceImpl;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import com.vito.shared.web.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T029 (US3): {@code POST /api/v1/events/{eventId}/rsvp} responde como los fixtures
 * {@code respondRsvp.*}. Controlador, handler de errores, ObjectMapper de Spring y servicio
 * reales; los repositorios y el cupo son mocks.
 */
@JsonTest
class RespondRsvpContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");
    private static final Pattern UTC_TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

    private static final String EVENT = "evt_01H8X2K4QJ";
    private static final String ORGANIZER = "usr_01H8X2K4QJ";
    private static final String GUEST = "usr_01H8X2K4QV";
    private static final String LATE_GUEST = "usr_01H8X2K4QW";

    @Autowired
    JsonMapper mapper;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final RSVPRepository rsvpRepository = mock(RSVPRepository.class);
    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private MockMvc mvc;
    private Event event;

    @BeforeEach
    void setUp() {
        EventServiceImpl service = new EventServiceImpl(eventRepository, rsvpRepository, invitationRepository,
                mock(EventPublisherPort.class), mock(QRCodeGenerator.class), attendanceService);
        mvc = buildMvc(service);
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        event = Event.builder().id(EVENT).organizerId(ORGANIZER).status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(5, ChronoUnit.HOURS)).build())
                .settings(Event.EventSettings.builder().isPublic(true).maxAttendees(150).build())
                .stats(Event.EventStats.builder().build())
                .build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        when(invitationRepository.findByEventIdAndInviteeId(anyString(), anyString())).thenReturn(Optional.empty());
    }

    private MockMvc buildMvc(EventServiceImpl service) {
        return MockMvcBuilders
                .standaloneSetup(new EventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- 200

    @Test
    void acceptedWithSeat_IsTheAcceptedFixture() throws Exception {
        authenticateAs(GUEST);
        when(attendanceService.confirm(EVENT, GUEST, "Ana", "¡Ahí estaré!")).thenReturn(RSVP.builder()
                .id("rsvp_interno").eventId(EVENT).userId(GUEST).displayName("Ana")
                .status(RSVP.RSVPStatus.ACCEPTED).message("¡Ahí estaré!")
                .respondedAt(Instant.parse("2026-10-28T02:10:00Z"))
                .checkInQrCode("qr-que-no-debe-salir").build());

        JsonNode body = respond(fixtureText("respondRsvp.request.json"), 200);

        assertEquals(fixture("respondRsvp.accepted.200.json"), body);
    }

    @Test
    void acceptedWithoutSeat_IsTheWaitlistedFixture() throws Exception {
        authenticateAs(LATE_GUEST);
        Instant at = Instant.parse("2026-10-28T02:12:00Z");
        when(attendanceService.confirm(EVENT, LATE_GUEST, "Ana", null)).thenReturn(RSVP.builder()
                .eventId(EVENT).userId(LATE_GUEST).status(RSVP.RSVPStatus.WAITLISTED)
                .respondedAt(at).waitlistedAt(at).build());

        JsonNode body = respond("{\"status\":\"ACCEPTED\"}", 200);

        assertEquals(fixture("respondRsvp.waitlisted.200.json"), body);
    }

    // ---- 400

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"WAITLISTED\"}", "{\"status\":\"PENDING\"}",
            "{\"status\":\"VOY\"}", "{\"message\":\"sin status\"}"})
    void anInvalidStatus_IsThe400Fixture(String request) throws Exception {
        authenticateAs(GUEST);

        assertErrorMatchesFixture("respondRsvp.400.json", respond(request, 400));
        verifyNoInteractions(attendanceService);
    }

    @Test
    void aMessageOver280Characters_Is400() throws Exception {
        authenticateAs(GUEST);

        JsonNode body = respond("{\"status\":\"ACCEPTED\",\"message\":\"" + "x".repeat(281) + "\"}", 400);

        assertEquals("VALIDATION_ERROR", body.get("error").asString());
        assertTrue(body.get("details").has("message"));
    }

    // ---- 401, 404, 409

    @Test
    void withoutAuthentication_IsThe401Fixture() throws Exception {
        assertErrorMatchesFixture("respondRsvp.401.json",
                perform(fixtureText("respondRsvp.request.json"), 401));
    }

    @Test
    void aMissingEvent_IsTheNotFoundFixture() throws Exception {
        authenticateAs(GUEST);
        when(eventRepository.findById(EVENT)).thenReturn(Optional.empty());

        assertErrorMatchesFixture("respondRsvp.notFound.404.json", respond("{\"status\":\"ACCEPTED\"}", 404));
    }

    @Test
    void aPrivateEventWithoutInvitation_IsThePrivateFixture() throws Exception {
        authenticateAs(GUEST);
        event.getSettings().setPublic(false);
        when(rsvpRepository.existsByEventIdAndUserId(EVENT, GUEST)).thenReturn(false);

        JsonNode body = respond("{\"status\":\"ACCEPTED\"}", 404);

        assertErrorMatchesFixture("respondRsvp.private.404.json", body);
        assertErrorMatchesFixture("respondRsvp.notFound.404.json", body);
    }

    @Test
    void theOrganizer_IsThe409Fixture() throws Exception {
        authenticateAs(ORGANIZER);

        assertErrorMatchesFixture("respondRsvp.409.json", respond("{\"status\":\"ACCEPTED\"}", 409));
    }

    /** T031 (D-01, FR-030): la ruta separada para responder invitaciones ya no existe. */
    @Test
    void theOldInvitationRespondRouteIsGone() throws Exception {
        authenticateAs(GUEST);

        MvcResult result = mvc.perform(post("/api/v1/events/" + EVENT + "/invitations/respond")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}")).andReturn();

        assertTrue(result.getResponse().getStatus() == 404 || result.getResponse().getStatus() == 405,
                "status " + result.getResponse().getStatus());
        verifyNoInteractions(attendanceService);
    }

    // ---- helpers

    /** Como el filtro JWT: principal con el nombre visible. */
    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "Ana"), null, List.of(new SimpleGrantedAuthority("USER"))));
    }

    private JsonNode respond(String request, int expectedStatus) throws Exception {
        return perform(request, expectedStatus);
    }

    private JsonNode perform(String request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events/" + EVENT + "/rsvp")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn();
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

    /** Envelope de ADR-003 igual al fixture; {@code timestamp} cambia en cada respuesta. */
    private void assertErrorMatchesFixture(String name, JsonNode actual) throws Exception {
        ObjectNode expected = (ObjectNode) fixture(name).deepCopy();
        ObjectNode body = (ObjectNode) actual.deepCopy();
        assertTrue(UTC_TIMESTAMP.matcher(body.remove("timestamp").asString()).matches());
        expected.remove("timestamp");
        assertEquals(expected, body);
    }
}
