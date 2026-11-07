package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.application.service.InviteLinkService;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.InviteLink;
import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.InviteLinkRepository;
import com.vito.event.infrastructure.config.InviteLinkSettings;
import com.vito.event.infrastructure.security.AuthenticatedUser;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T036 (US1): las 5 operaciones de enlaces responden como los fixtures {@code *InviteLink*}.
 * Controlador, servicio, handler de errores y ObjectMapper reales; repositorios en mock y la
 * hora fija de los fixtures.
 */
@JsonTest
class InviteLinkContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");
    private static final Pattern UTC_TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    /** Dominio de prueba: el real nunca va al código (FR-002). */
    private static final String BASE_URL = "https://app.example.test";

    private static final String EVENT = "evt_01H8X2K4QJ";
    private static final String ORGANIZER = "usr_01H8X2K4QJ";
    private static final String GUEST = "usr_01H8X2K4QV";
    private static final String TOKEN = "k3J9xQ2mV7pL1sT8wY4zBc";
    private static final String LINK_ID = "inl_01HA1B2C3D";

    @Autowired
    JsonMapper mapper;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final InviteLinkRepository inviteLinkRepository = mock(InviteLinkRepository.class);
    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private MockMvc mvc;
    private Event event;
    private InviteLink link;

    private MockMvc mvcAt(String instant) {
        InviteLinkService service = new InviteLinkService(eventRepository, inviteLinkRepository, invitationRepository,
                attendanceService, mock(EventPublisherPort.class), Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        return MockMvcBuilders.standaloneSetup(new InviteLinkController(service, new InviteLinkSettings(BASE_URL)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @BeforeEach
    void setUp() {
        mvc = mvcAt("2026-10-28T02:10:00Z");
        event = Event.builder().id(EVENT).organizerId(ORGANIZER).organizerDisplayName("Bernardo")
                .title("Cumple de Caro").status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder()
                        .startDate(Instant.parse("2026-12-26T01:00:00Z")).endDate(Instant.parse("2026-12-26T07:00:00Z"))
                        .location("Cartagena de Indias").address("Dirección exacta").latitude(10.4).longitude(-75.5)
                        .category("BIRTHDAY").build())
                .settings(Event.EventSettings.builder().isPublic(false).hideAddressUntilAccepted(false).build())
                .build();
        link = InviteLink.builder().id(LINK_ID).eventId(EVENT).token(TOKEN).createdBy(ORGANIZER)
                .expiresAt(Instant.parse("2026-11-03T15:00:00Z")).maxUses(50).uses(0)
                .createdAt(Instant.parse("2026-10-27T15:00:00Z")).build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        when(inviteLinkRepository.findByToken(TOKEN)).thenReturn(Optional.of(link));
        when(inviteLinkRepository.findByToken(anyString())).thenAnswer(inv ->
                TOKEN.equals(inv.getArgument(0)) ? Optional.of(link) : Optional.empty());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- createInviteLink

    @Test
    void create_IsThe201Fixture() throws Exception {
        mvc = mvcAt("2026-10-27T15:00:00Z");
        authenticateAs(ORGANIZER);
        when(inviteLinkRepository.save(any())).thenAnswer(inv -> ((InviteLink) inv.getArgument(0)).toBuilder().id(LINK_ID).build());

        ObjectNode body = (ObjectNode) perform(post("/api/v1/events/" + EVENT + "/invite-links")
                .contentType(MediaType.APPLICATION_JSON).content(fixtureText("createInviteLink.request.json")), 201);

        // El token es aleatorio: se comprueba su forma y que la url lo use
        String token = body.get("token").asString();
        assertTrue(token.matches("^[A-Za-z0-9_-]{22}$"), token);
        assertEquals(BASE_URL + "/i/" + token, body.get("url").asString());
        assertEquals(withoutTokenAndUrl(fixture("createInviteLink.201.json")), withoutTokenAndUrl(body));
    }

    @Test
    void create_WithoutBodyIs201() throws Exception {
        authenticateAs(ORGANIZER);
        when(inviteLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JsonNode body = perform(post("/api/v1/events/" + EVENT + "/invite-links"), 201);

        assertEquals("2026-11-04T02:10:00Z", body.get("expiresAt").asString());
        assertTrue(body.get("maxUses").isNull());
    }

    @Test
    void create_Errors_AreTheFixtures() throws Exception {
        authenticateAs(ORGANIZER);
        assertErrorMatchesFixture("createInviteLink.400.json", perform(post("/api/v1/events/" + EVENT + "/invite-links")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiresInDays\":31}"), 400));

        authenticateAs(GUEST);
        assertErrorMatchesFixture("createInviteLink.403.json", perform(post("/api/v1/events/" + EVENT + "/invite-links"), 403));

        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());
        assertErrorMatchesFixture("createInviteLink.404.json", perform(post("/api/v1/events/evt_x/invite-links"), 404));

        authenticateAs(ORGANIZER);
        event.setStatus(Event.EventStatus.CANCELLED);
        assertErrorMatchesFixture("createInviteLink.409.json", perform(post("/api/v1/events/" + EVENT + "/invite-links"), 409));

        SecurityContextHolder.clearContext();
        assertErrorMatchesFixture("createInviteLink.401.json", perform(post("/api/v1/events/" + EVENT + "/invite-links"), 401));
    }

    // ---- listInviteLinks

    @Test
    void list_IsThe200Fixture() throws Exception {
        authenticateAs(ORGANIZER);
        InviteLink used = link.toBuilder().uses(3).build();
        InviteLink revoked = InviteLink.builder().id("inl_01HA1B2C3E").eventId(EVENT).token("m8N2pR5tV9wX1yZ3aB4cDe")
                .expiresAt(Instant.parse("2026-11-03T15:00:00Z")).maxUses(null).uses(0).revoked(true)
                .createdAt(Instant.parse("2026-10-26T15:00:00Z")).build();
        when(inviteLinkRepository.findByEventId(EVENT, Instant.parse("2026-10-28T02:10:00Z"), 0, 20))
                .thenReturn(List.of(used, revoked));

        JsonNode body = perform(get("/api/v1/events/" + EVENT + "/invite-links"), 200);

        assertTrue(body.isArray());
        ArrayNode expected = (ArrayNode) fixture("listInviteLinks.200.json");
        assertEquals(expected.size(), body.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(BASE_URL + "/i/" + expected.get(i).get("token").asString(), body.get(i).get("url").asString());
            assertEquals(withoutUrl(expected.get(i)), withoutUrl(body.get(i)));
        }
    }

    @Test
    void list_Errors_AreTheFixtures() throws Exception {
        authenticateAs(ORGANIZER);
        assertErrorMatchesFixture("listInviteLinks.400.json",
                perform(get("/api/v1/events/" + EVENT + "/invite-links").param("size", "101"), 400));
        authenticateAs(GUEST);
        assertErrorMatchesFixture("listInviteLinks.403.json", perform(get("/api/v1/events/" + EVENT + "/invite-links"), 403));
        when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());
        assertErrorMatchesFixture("listInviteLinks.404.json", perform(get("/api/v1/events/evt_x/invite-links"), 404));
        SecurityContextHolder.clearContext();
        assertErrorMatchesFixture("listInviteLinks.401.json", perform(get("/api/v1/events/" + EVENT + "/invite-links"), 401));
    }

    // ---- revokeInviteLink

    @Test
    void revoke_Is204WithoutBodyAndTheErrorsAreTheFixtures() throws Exception {
        authenticateAs(ORGANIZER);
        when(inviteLinkRepository.findById(LINK_ID)).thenReturn(Optional.of(link));
        MvcResult ok = mvc.perform(delete("/api/v1/events/" + EVENT + "/invite-links/" + LINK_ID)).andReturn();
        assertEquals(204, ok.getResponse().getStatus());
        assertEquals("", ok.getResponse().getContentAsString());

        when(inviteLinkRepository.findById("inl_x")).thenReturn(Optional.empty());
        assertErrorMatchesFixture("revokeInviteLink.404.json",
                perform(delete("/api/v1/events/" + EVENT + "/invite-links/inl_x"), 404));
        authenticateAs(GUEST);
        assertErrorMatchesFixture("revokeInviteLink.403.json",
                perform(delete("/api/v1/events/" + EVENT + "/invite-links/" + LINK_ID), 403));
        SecurityContextHolder.clearContext();
        assertErrorMatchesFixture("revokeInviteLink.401.json",
                perform(delete("/api/v1/events/" + EVENT + "/invite-links/" + LINK_ID), 401));
    }

    // ---- getInviteLinkPreview (pública)

    @Test
    void preview_IsThe200FixtureWithoutSessionAndWithoutAddress() throws Exception {
        JsonNode body = perform(get("/api/v1/invite-links/" + TOKEN), 200);

        assertEquals(fixture("getInviteLinkPreview.200.json"), body);
        assertFalse(body.toString().contains("Dirección exacta"));
        assertFalse(body.has("latitude"));
    }

    @Test
    void preview_UnknownTokenIsThe404Fixture() throws Exception {
        assertErrorMatchesFixture("getInviteLinkPreview.404.json",
                perform(get("/api/v1/invite-links/" + "z".repeat(22)), 404));
    }

    // ---- acceptInviteLink

    @Test
    void accept_IsThe200Fixture() throws Exception {
        authenticateAs(GUEST);
        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.empty());
        when(inviteLinkRepository.tryUse(LINK_ID, Instant.parse("2026-10-28T02:10:00Z"))).thenReturn(true);
        when(invitationRepository.save(any())).thenAnswer(inv ->
                ((Invitation) inv.getArgument(0)).toBuilder().id("inv_01HA9Z8Y7X").build());
        when(attendanceService.confirm(EVENT, GUEST, "Ana", null))
                .thenReturn(RSVP.builder().status(RSVP.RSVPStatus.ACCEPTED).build());

        assertEquals(fixture("acceptInviteLink.200.json"), perform(post("/api/v1/invite-links/" + TOKEN + "/accept"), 200));
    }

    @Test
    void accept_Errors_AreTheFixtures() throws Exception {
        assertErrorMatchesFixture("acceptInviteLink.401.json", perform(post("/api/v1/invite-links/" + TOKEN + "/accept"), 401));

        authenticateAs(GUEST);
        assertErrorMatchesFixture("acceptInviteLink.404.json",
                perform(post("/api/v1/invite-links/" + "z".repeat(22) + "/accept"), 404));

        when(invitationRepository.findByEventIdAndInviteeId(EVENT, GUEST)).thenReturn(Optional.empty());
        when(inviteLinkRepository.tryUse(anyString(), any())).thenReturn(false);
        assertErrorMatchesFixture("acceptInviteLink.max-uses.409.json",
                perform(post("/api/v1/invite-links/" + TOKEN + "/accept"), 409));

        event.setStatus(Event.EventStatus.CANCELLED);
        assertErrorMatchesFixture("acceptInviteLink.event-not-active.409.json",
                perform(post("/api/v1/invite-links/" + TOKEN + "/accept"), 409));
    }

    // ---- helpers

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "Ana"), null, List.of(new SimpleGrantedAuthority("USER"))));
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

    private static JsonNode withoutUrl(JsonNode node) {
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.remove("url");
        return copy;
    }

    private static JsonNode withoutTokenAndUrl(JsonNode node) {
        ObjectNode copy = (ObjectNode) withoutUrl(node);
        copy.remove("token");
        return copy;
    }

    /** Envelope de ADR-003 igual al fixture; {@code timestamp} cambia en cada respuesta. */
    private void assertErrorMatchesFixture(String name, JsonNode actual) throws Exception {
        ObjectNode expected = (ObjectNode) fixture(name).deepCopy();
        ObjectNode body = (ObjectNode) actual.deepCopy();
        assertTrue(UTC_TIMESTAMP.matcher(body.remove("timestamp").asString()).matches(), name);
        expected.remove("timestamp");
        assertEquals(expected, body, name);
    }
}
