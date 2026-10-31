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
import static org.mockito.Mockito.verifyNoInteractions;
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
        mvc = MockMvcBuilders.standaloneSetup(new EventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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

    /** T-B29: sin @Valid, un title vacío llegaba al servicio y el 400 del fixture nunca se producía. */
    @Test
    void createEventWithoutTitleReturns400() throws Exception {
        mvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.title").exists());

        verifyNoInteractions(eventService);
    }

    @Test
    void createEventWithShortTitleReturns400LikeFixture() throws Exception {
        mvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.title").value("Mínimo 3 caracteres"));

        verifyNoInteractions(eventService);
    }
}
