package com.vito.streaming.infrastructure.adapter.inbound.rest;

import com.vito.streaming.domain.exception.StreamingException;
import com.vito.streaming.domain.port.inbound.StreamingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T-B03: los errores de streaming-service salen con el envelope de ADR-003 (CONVENTIONS §3.3). */
class GlobalExceptionHandlerTest {

    private final StreamingService service = mock(StreamingService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StreamingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TestingAuthenticationToken user = new TestingAuthenticationToken("usr_1", null);
        user.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(user);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void badRequestIs400ValidationError() throws Exception {
        assertEnvelope(mvc.perform(post("/api/v1/streaming").contentType(MediaType.APPLICATION_JSON).content("{")), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    void missingResourceIs404NotFound() throws Exception {
        when(service.getStream(any())).thenThrow(StreamingException.streamNotFound());

        assertEnvelope(mvc.perform(get("/api/v1/streaming/str_x")), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.message").value("La transmisión no existe"));
    }

    @Test
    void unknownRouteIs404NotFound() throws Exception {
        assertEnvelope(mvc.perform(get("/api/v1/streaming/no/existe/esta/ruta")), 404, "NOT_FOUND");
    }

    @Test
    void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
        when(service.getStream(any())).thenThrow(new IllegalStateException("fallo en mongo://secreto"));

        assertEnvelope(mvc.perform(get("/api/v1/streaming/str_x")), 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message", not(containsString("mongo://secreto"))));
    }

    @Test
    void missingAuthenticationIs401Unauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        assertEnvelope(mvc.perform(post("/api/v1/streaming/str_x/start")), 401, "UNAUTHORIZED");
    }

    private static ResultActions assertEnvelope(ResultActions result, int status, String error) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp", endsWith("Z")));
    }
}
