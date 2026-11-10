package com.vito.shared.web.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Envelope de ADR-003 para los errores genéricos y para las excepciones de dominio. */
class BaseExceptionHandlerTest {

    enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    static class DomainException extends RuntimeException {
        final Reason reason;
        final Map<String, String> details;

        DomainException(Reason reason, String message, Map<String, String> details) {
            super(message);
            this.reason = reason;
            this.details = details;
        }
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {
        @GetMapping("/domain")
        String domain(@RequestParam Reason reason) {
            throw new DomainException(reason, "Mensaje de " + reason,
                    reason == Reason.INVALID ? Map.of("field", "Detalle") : null);
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/header")
        String header(@RequestHeader("X-Required") String value) {
            return value;
        }

        @GetMapping("/number")
        String number(@RequestParam int n) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("token secreto-123 en la ruta");
        }
    }

    @RestControllerAdvice
    static class TestHandler extends BaseExceptionHandler {
        @ExceptionHandler(DomainException.class)
        ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
            return domainError(ex.reason, ex.getMessage(), ex.details);
        }

        @Override
        protected String redact(String message) {
            return message == null ? null : message.replace("secreto-123", "***");
        }
    }

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new TestHandler())
                .build();
    }

    @Test
    void domainReasonsMapToStatusAndCode() throws Exception {
        Object[][] cases = {
                {Reason.INVALID, 400, "VALIDATION_ERROR"},
                {Reason.UNAUTHORIZED, 401, "UNAUTHORIZED"},
                {Reason.FORBIDDEN, 403, "FORBIDDEN"},
                {Reason.NOT_FOUND, 404, "NOT_FOUND"},
                {Reason.CONFLICT, 409, "CONFLICT"},
        };
        for (Object[] c : cases) {
            mvc.perform(get("/domain").param("reason", c[0].toString()))
                    .andExpect(status().is((int) c[1]))
                    .andExpect(jsonPath("$.error").value(c[2]))
                    .andExpect(jsonPath("$.message").value("Mensaje de " + c[0]))
                    .andExpect(jsonPath("$.status").value(c[1]))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
        mvc.perform(get("/domain").param("reason", "INVALID"))
                .andExpect(jsonPath("$.details.field").value("Detalle"));
        mvc.perform(get("/domain").param("reason", "NOT_FOUND"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void beanValidationListsTheFields() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El campo name no es válido"))
                .andExpect(jsonPath("$.details.name").isNotEmpty());
    }

    @Test
    void malformedJsonMissingHeaderAndWrongTypeAre400() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es un JSON válido"));
        mvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el header X-Required"));
        mvc.perform(get("/number").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void springClientErrorsKeepTheirStatus() throws Exception {
        mvc.perform(post("/number"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(get("/number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedErrorsAre500WithAGenericMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(BaseExceptionHandler.UNEXPECTED_MESSAGE));
    }
}
