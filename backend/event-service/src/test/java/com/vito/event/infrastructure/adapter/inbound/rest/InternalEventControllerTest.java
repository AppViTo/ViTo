package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.application.service.AttendanceService;
import com.vito.event.application.service.EventServiceImpl;
import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.EventPublisherPort;
import com.vito.event.domain.port.outbound.EventRepository;
import com.vito.event.domain.port.outbound.InvitationRepository;
import com.vito.event.domain.port.outbound.InviteLinkRepository;
import com.vito.event.domain.port.outbound.QRCodeGenerator;
import com.vito.event.domain.port.outbound.RSVPRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T-B33: la ruta INTERNO de acceso que usa media-service; solo informa del usuario del token. */
@ExtendWith(MockitoExtension.class)
class InternalEventControllerTest {

    private static final String URL = "/internal/v1/events/{eventId}/my-access";

    @Mock private EventRepository eventRepository;
    @Mock private RSVPRepository rsvpRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private EventPublisherPort eventPublisher;
    @Mock private QRCodeGenerator qrCodeGenerator;
    @Mock private AttendanceService attendanceService;
    @Mock private InviteLinkRepository inviteLinkRepository;

    @InjectMocks
    private EventServiceImpl eventService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalEventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(eventRepository.findById("evt_1"))
                .thenReturn(Optional.of(Event.builder().id("evt_1").organizerId("usr_org").title("Boda").build()));
        lenient().when(eventRepository.findById("evt_x")).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String userId) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(userId, null);
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    void returnsTheOrganizerAndTheCallersRsvp() throws Exception {
        loginAs("usr_guest");
        when(rsvpRepository.findByEventIdAndUserId("evt_1", "usr_guest"))
                .thenReturn(Optional.of(RSVP.builder().eventId("evt_1").userId("usr_guest")
                        .status(RSVP.RSVPStatus.ACCEPTED).build()));

        mvc.perform(get(URL, "evt_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt_1"))
                .andExpect(jsonPath("$.organizerId").value("usr_org"))
                .andExpect(jsonPath("$.rsvpStatus").value("ACCEPTED"));
        verify(rsvpRepository).findByEventIdAndUserId("evt_1", "usr_guest");
    }

    @Test
    void withoutRsvpTheStatusIsNull() throws Exception {
        loginAs("usr_other");
        when(rsvpRepository.findByEventIdAndUserId(anyString(), any())).thenReturn(Optional.empty());

        mvc.perform(get(URL, "evt_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").doesNotExist());
    }

    @Test
    void unknownEventIs404() throws Exception {
        loginAs("usr_guest");

        mvc.perform(get(URL, "evt_x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void withoutAuthenticationIs401() throws Exception {
        mvc.perform(get(URL, "evt_1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
