package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.model.Event;
import com.vito.event.domain.port.inbound.EventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T-B15: createEvent responde 201 (CONVENTIONS §4, ADR-005), como el fixture createEvent.201. */
class EventControllerContractTest {

    private static final Path REQUEST = Path.of("..", "..", "docs", "api", "fixtures", "events",
            "createEvent.request.json");

    private final EventService eventService = mock(EventService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EventController(eventService)).build();
        TestingAuthenticationToken organizer = new TestingAuthenticationToken("usr_1", null);
        organizer.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(organizer);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createEventReturns201() throws Exception {
        when(eventService.createEvent(any())).thenReturn(Event.builder().id("evt_1").title("Los 30 de Marta").build());

        mvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(REQUEST)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("evt_1"));
    }
}
