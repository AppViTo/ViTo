package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.application.service.EventServiceImpl;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import com.vito.event.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/** T053 (US5): {@code DELETE /api/v1/events/{eventId}} responde como los fixtures {@code cancelEvent.*}. */
@JsonTest
class CancelEventContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "events");
    private static final Pattern UTC_TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    private static final String EVENT = "evt_01H9ZP5RKM";
    private static final String ORGANIZER = "usr_01H8X2K4QJ";

    @Autowired
    JsonMapper mapper;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private MockMvc mvc;
    private Event event;

    @BeforeEach
    void setUp() {
        EventServiceImpl service = new EventServiceImpl(eventRepository, mock(RSVPRepository.class),
                mock(InvitationRepository.class), mock(EventPublisherPort.class), mock(QRCodeGenerator.class),
                mock(AttendanceService.class));
        mvc = MockMvcBuilders.standaloneSetup(new EventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        event = Event.builder().id(EVENT).organizerId(ORGANIZER).status(Event.EventStatus.PUBLISHED)
                .details(Event.EventDetails.builder().startDate(start).endDate(start.plus(8, ChronoUnit.HOURS)).build())
                .build();
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        when(eventRepository.cancel(eq(EVENT), any())).thenReturn(true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cancelIs204WithoutBodyAndCancellingAgainToo() throws Exception {
        authenticateAs(ORGANIZER);
        assertNoContent();

        event.setStatus(Event.EventStatus.CANCELLED);
        assertNoContent();
    }

    @Test
    void errorsAreTheFixtures() throws Exception {
        authenticateAs("usr_otro");
        assertErrorMatchesFixture("cancelEvent.403.json", 403);

        authenticateAs(ORGANIZER);
        event.setStatus(Event.EventStatus.COMPLETED);
        assertErrorMatchesFixture("cancelEvent.409.json", 409);

        when(eventRepository.findById(EVENT)).thenReturn(Optional.empty());
        assertErrorMatchesFixture("cancelEvent.404.json", 404);

        SecurityContextHolder.clearContext();
        assertErrorMatchesFixture("cancelEvent.401.json", 401);
    }

    private void assertNoContent() throws Exception {
        MvcResult result = mvc.perform(delete("/api/v1/events/" + EVENT)).andReturn();
        assertEquals(204, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString());
        // cancelEvent.204.json es null: sin cuerpo
        assertTrue(mapper.readTree(Files.readString(FIXTURES.resolve("cancelEvent.204.json"))).isNull());
    }

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "Alex Villa"), null, List.of(new SimpleGrantedAuthority("USER"))));
    }

    private void assertErrorMatchesFixture(String name, int status) throws Exception {
        MvcResult result = mvc.perform(delete("/api/v1/events/" + EVENT)).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(status, result.getResponse().getStatus(), content);
        ObjectNode body = (ObjectNode) mapper.readTree(content);
        ObjectNode expected = (ObjectNode) mapper.readTree(Files.readString(FIXTURES.resolve(name)));
        assertTrue(UTC_TIMESTAMP.matcher(body.remove("timestamp").asString()).matches(), name);
        expected.remove("timestamp");
        assertEquals(expected, (JsonNode) body, name);
    }
}
