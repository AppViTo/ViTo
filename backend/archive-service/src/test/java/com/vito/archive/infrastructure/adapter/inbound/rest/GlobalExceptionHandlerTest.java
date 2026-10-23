package com.vito.archive.infrastructure.adapter.inbound.rest;

import com.vito.archive.domain.exception.ArchiveException;
import com.vito.archive.domain.port.inbound.ArchiveService;
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

/** T-B03: los errores de archive-service salen con el envelope de ADR-003 (CONVENTIONS §3.3). */
class GlobalExceptionHandlerTest {

    private final ArchiveService service = mock(ArchiveService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ArchiveController(service))
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
        assertEnvelope(mvc.perform(post("/api/v1/archive").contentType(MediaType.APPLICATION_JSON).content("{")), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    void missingResourceIs404NotFound() throws Exception {
        when(service.getArchive(any())).thenThrow(ArchiveException.archiveNotFound());

        assertEnvelope(mvc.perform(get("/api/v1/archive/arc_x")), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.message").value("El archivo del evento no existe"));
    }

    @Test
    void unknownRouteIs404NotFound() throws Exception {
        assertEnvelope(mvc.perform(get("/api/v1/archive/no/existe/esta/ruta")), 404, "NOT_FOUND");
    }

    @Test
    void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
        when(service.getArchive(any())).thenThrow(new IllegalStateException("fallo en mongo://secreto"));

        assertEnvelope(mvc.perform(get("/api/v1/archive/arc_x")), 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message", not(containsString("mongo://secreto"))));
    }

    @Test
    void missingAuthenticationIs401Unauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        assertEnvelope(mvc.perform(get("/api/v1/archive/my")), 401, "UNAUTHORIZED");
    }

    private static ResultActions assertEnvelope(ResultActions result, int status, String error) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp", endsWith("Z")));
    }
}
