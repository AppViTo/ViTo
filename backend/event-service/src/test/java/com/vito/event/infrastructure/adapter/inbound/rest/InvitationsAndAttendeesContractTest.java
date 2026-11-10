package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.application.service.EventServiceImpl;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.Invitation;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T050 y T056: {@code sendInvitation}, {@code listEventInvitations} y {@code listEventAttendees}
 * responden como sus fixtures.
 */
@JsonTest
class InvitationsAndAttendeesContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");
    private static final Pattern UTC_TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    private static final String EVENT = "evt_01H8X2K4QJ";
    private static final String ORGANIZER = "usr_01H8X2K4QJ";
    private static final String GUEST = "usr_01H8X2K4QV";

    @Autowired
    JsonMapper mapper;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final RSVPRepository rsvpRepository = mock(RSVPRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EventServiceImpl service = new EventServiceImpl(eventRepository, rsvpRepository, invitationRepository,
                mock(EventPublisherPort.class), mock(QRCodeGenerator.class), mock(AttendanceService.class));
        mvc = MockMvcBuilders.standaloneSetup(new EventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder().id(EVENT).organizerId(ORGANIZER).status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(8, ChronoUnit.HOURS)).build())
                .build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());
        when(invitationRepository.save(any())).thenAnswer(call ->
                ((Invitation) call.getArgument(0)).toBuilder().id("inv_01HA1B2C3D").build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- sendInvitation

    @Test
    void sendInvitationWithTheFixtureRequestIs201AsTheFixture() throws Exception {
        authenticateAs(ORGANIZER);

        MvcResult result = mvc.perform(sendRequest(EVENT, Files.readString(FIXTURES.resolve("sendInvitation.request.json"))))
                .andReturn();

        assertEquals(201, result.getResponse().getStatus());
        ObjectNode body = (ObjectNode) mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Instant sentAt = Instant.parse(body.remove("sentAt").asString());
        Instant expiresAt = Instant.parse(body.remove("expiresAt").asString());
        assertEquals(Duration.ofDays(7), Duration.between(sentAt, expiresAt));
        ObjectNode expected = (ObjectNode) fixture("sendInvitation.201.json");
        expected.remove("sentAt");
        expected.remove("expiresAt");
        assertEquals(expected, (JsonNode) body);
    }

    @Test
    void sendInvitationErrorsAreTheFixtures() throws Exception {
        authenticateAs(ORGANIZER);
        assertError(sendRequest(EVENT, "{\"message\":\"Hola\"}"), "sendInvitation.400.json", 400);
        assertError(sendRequest("evt_x", "{\"inviteeId\":\"" + GUEST + "\"}"), "sendInvitation.404.json", 404);

        when(invitationRepository.existsByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(true);
        assertError(sendRequest(EVENT, "{\"inviteeId\":\"" + GUEST + "\"}"), "sendInvitation.409.json", 409);

        authenticateAs("usr_otro");
        assertError(sendRequest(EVENT, "{\"inviteeId\":\"" + GUEST + "\"}"), "sendInvitation.403.json", 403);

        SecurityContextHolder.clearContext();
        assertError(sendRequest(EVENT, "{\"inviteeId\":\"" + GUEST + "\"}"), "sendInvitation.401.json", 401);
    }

    // ---- listEventInvitations

    @Test
    void listEventInvitationsIsThe200Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        ArrayNode expected = (ArrayNode) fixture("listEventInvitations.200.json");
        // La PENDING del fixture vence el 2026-11-04 y EXPIRED se calcula al leer: se corre al futuro
        Instant notYetDue = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        ((ObjectNode) expected.get(0)).put("expiresAt", notYetDue.toString());
        List<Invitation> stored = List.of(
                mapper.treeToValue(expected.get(0), Invitation.class),
                mapper.treeToValue(expected.get(1), Invitation.class));
        when(invitationRepository.findByEventId(EVENT, 0, 20)).thenReturn(stored);

        MvcResult result = mvc.perform(get("/api/v1/events/" + EVENT + "/invitations")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(expected, mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)));
    }

    @Test
    void listEventInvitationsErrorsAreTheFixtures() throws Exception {
        authenticateAs(ORGANIZER);
        assertError(get("/api/v1/events/" + EVENT + "/invitations").param("size", "101"), "listEventInvitations.400.json", 400);
        assertError(get("/api/v1/events/evt_x/invitations"), "listEventInvitations.404.json", 404);

        authenticateAs(GUEST);
        assertError(get("/api/v1/events/" + EVENT + "/invitations"), "listEventInvitations.403.json", 403);

        SecurityContextHolder.clearContext();
        assertError(get("/api/v1/events/" + EVENT + "/invitations"), "listEventInvitations.401.json", 401);
    }

    // ---- listEventAttendees (US6)

    @Test
    void listEventAttendeesIsThe200FixtureWithoutInternalFields() throws Exception {
        authenticateAs(ORGANIZER);
        // Campos internos que nunca deben salir: id, mensaje, QR y check-in
        RSVP accepted = RSVP.builder().id("rsvp_1").eventId(EVENT).userId(GUEST).displayName("Carolina Martínez")
                .status(RSVP.RSVPStatus.ACCEPTED).message("Ahí estaré").checkInQrCode("qr-secreto")
                .respondedAt(Instant.parse("2026-10-28T02:10:00Z")).build();
        RSVP waitlisted = RSVP.builder().id("rsvp_2").eventId(EVENT).userId("usr_01H8X2K4QW")
                .status(RSVP.RSVPStatus.WAITLISTED).respondedAt(Instant.parse("2026-10-28T02:12:00Z"))
                .waitlistedAt(Instant.parse("2026-10-28T02:12:00Z")).build();
        when(rsvpRepository.findByEventId(EVENT, null, 0, 20)).thenReturn(List.of(accepted, waitlisted));

        MvcResult result = mvc.perform(get("/api/v1/events/" + EVENT + "/attendees")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(fixture("listEventAttendees.200.json"),
                mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)));
    }

    @Test
    void theStatusFilterReachesTheRepository() throws Exception {
        authenticateAs(ORGANIZER);
        when(rsvpRepository.findByEventId(EVENT, RSVP.RSVPStatus.WAITLISTED, 0, 20)).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/api/v1/events/" + EVENT + "/attendees").param("status", "WAITLISTED"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals("[]", result.getResponse().getContentAsString());
    }

    @Test
    void listEventAttendeesErrorsAreTheFixtures() throws Exception {
        authenticateAs(ORGANIZER);
        assertError(get("/api/v1/events/" + EVENT + "/attendees").param("status", "GOING"), "listEventAttendees.400.json", 400);
        assertError(get("/api/v1/events/evt_x/attendees"), "listEventAttendees.404.json", 404);

        // Aunque esté confirmado
        authenticateAs(GUEST);
        when(rsvpRepository.findByEventIdAndUserId(anyString(), anyString()))
                .thenReturn(Optional.of(RSVP.builder().status(RSVP.RSVPStatus.ACCEPTED).build()));
        assertError(get("/api/v1/events/" + EVENT + "/attendees"), "listEventAttendees.403.json", 403);

        SecurityContextHolder.clearContext();
        assertError(get("/api/v1/events/" + EVENT + "/attendees"), "listEventAttendees.401.json", 401);
    }

    // ----

    private static RequestBuilder sendRequest(String eventId, String body) {
        return post("/api/v1/events/" + eventId + "/invitations").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode fixture(String name) throws Exception {
        return mapper.readTree(Files.readString(FIXTURES.resolve(name)));
    }

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "Alex Villa"), null, List.of(new SimpleGrantedAuthority("USER"))));
    }

    private void assertError(RequestBuilder request, String name, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(status, result.getResponse().getStatus(), name + ": " + content);
        ObjectNode body = (ObjectNode) mapper.readTree(content);
        ObjectNode expected = (ObjectNode) fixture(name);
        assertTrue(UTC_TIMESTAMP.matcher(body.remove("timestamp").asString()).matches(), name);
        expected.remove("timestamp");
        assertEquals(expected, (JsonNode) body, name);
    }
}
