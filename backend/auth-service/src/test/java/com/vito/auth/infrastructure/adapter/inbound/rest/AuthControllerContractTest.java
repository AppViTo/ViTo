package com.vito.auth.infrastructure.adapter.inbound.rest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.exception.UserAlreadyExistsException;
import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.inbound.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-B15: códigos HTTP y errores de auth-service comparados con docs/api/fixtures/authentication
 * (ADR-003, ADR-005, ADR-009). Mismas claves, mismo {@code status} y mismo {@code error}; el
 * {@code message} también cuando el fixture fija un texto que no depende del dato enviado.
 */
class AuthControllerContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "authentication");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuthService authService = mock(AuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        AuthService.AuthResult result = AuthService.AuthResult.builder()
                .accessToken("access").refreshToken("refresh").expiresIn(86400L)
                .user(User.builder().id("usr_1").username("alex_villa").email("alex@vito.com").build())
                .build();
        when(authService.register(any())).thenReturn(result);
        when(authService.login(any())).thenReturn(result);
        when(authService.refreshToken(any())).thenReturn(result);
    }

    // ── registerUser ────────────────────────────────────────────

    @Test
    void registerUser201() throws Exception {
        postJson("/api/v1/auth/register", fixture("registerUser.request.json"))
                .andExpect(status().isCreated());
    }

    @Test
    void registerUser400WithInvalidEmail() throws Exception {
        String body = "{\"username\":\"alex_villa\",\"email\":\"no-es-un-email\",\"password\":\"vito-dev-2026\"}";
        JsonNode error = errorBody(postJson("/api/v1/auth/register", body), 400);

        assertMatchesFixture(error, "registerUser.400.json");
        assertEquals(Set.of("email"), fieldNames(error.get("details")));
        assertEquals("Debe ser un correo electrónico válido", error.get("details").get("email").asText());
    }

    @Test
    void registerUser400WithShortPasswordAndUsername() throws Exception {
        String body = "{\"username\":\"al\",\"email\":\"alex@vito.com\",\"password\":\"corta\"}";
        JsonNode error = errorBody(postJson("/api/v1/auth/register", body), 400);

        assertMatchesFixture(error, "registerUser.400.json");
        assertEquals(Set.of("username", "password"), fieldNames(error.get("details")));
    }

    @Test
    void registerUser409WhenEmailIsTaken() throws Exception {
        when(authService.register(any())).thenThrow(UserAlreadyExistsException.emailTaken());

        JsonNode error = errorBody(postJson("/api/v1/auth/register", fixture("registerUser.request.json")), 409);

        assertMatchesFixture(error, "registerUser.409.json");
        assertEquals(fixtureJson("registerUser.409.json").get("message"), error.get("message"));
    }

    @Test
    void registerUser409WhenUsernameIsTaken() throws Exception {
        when(authService.register(any())).thenThrow(UserAlreadyExistsException.usernameTaken());

        JsonNode error = errorBody(postJson("/api/v1/auth/register", fixture("registerUser.request.json")), 409);

        assertMatchesFixture(error, "registerUser.409.json");
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        JsonNode error = errorBody(postJson("/api/v1/auth/register", "{\"email\":"), 400);

        assertEquals("VALIDATION_ERROR", error.get("error").asText());
        assertEnvelope(error, 400);
    }

    // ── loginUser ───────────────────────────────────────────────

    @Test
    void loginUser200() throws Exception {
        postJson("/api/v1/auth/login", fixture("loginUser.request.json"))
                .andExpect(status().isOk());
    }

    @Test
    void loginUser400WithoutPassword() throws Exception {
        JsonNode error = errorBody(postJson("/api/v1/auth/login", "{\"email\":\"alex@vito.com\"}"), 400);

        assertMatchesFixture(error, "loginUser.400.json");
        assertEquals(Set.of("password"), fieldNames(error.get("details")));
    }

    @Test
    void loginUser401WithWrongCredentials() throws Exception {
        when(authService.login(any())).thenThrow(AuthenticationException.invalidCredentials());

        JsonNode error = errorBody(postJson("/api/v1/auth/login", fixture("loginUser.request.json")), 401);

        assertMatchesFixture(error, "loginUser.401.json");
        assertEquals(fixtureJson("loginUser.401.json").get("message"), error.get("message"));
    }

    // ── refreshToken ────────────────────────────────────────────

    @Test
    void refreshToken200() throws Exception {
        postJson("/api/v1/auth/refresh", fixture("refreshToken.request.json"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshToken401WithInvalidToken() throws Exception {
        when(authService.refreshToken(any())).thenThrow(AuthenticationException.invalidRefreshToken());

        JsonNode error = errorBody(postJson("/api/v1/auth/refresh", fixture("refreshToken.request.json")), 401);

        assertMatchesFixture(error, "refreshToken.401.json");
        assertEquals(fixtureJson("refreshToken.401.json").get("message"), error.get("message"));
    }

    // ── logoutUser ──────────────────────────────────────────────

    /** ADR-004 y CONVENTIONS §4: 204. openapi dice 200 y se corrige en T-C08 (manda el ADR). */
    @Test
    void logoutUser204WithoutBody() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer access"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertEquals("", result.getResponse().getContentAsString());
    }

    @Test
    void logoutUser401WithInvalidToken() throws Exception {
        doThrow(AuthenticationException.invalidAccessToken()).when(authService).logout(anyString());

        JsonNode error = errorBody(mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer x")), 401);

        assertMatchesFixture(error, "logoutUser.401.json");
        assertEquals(fixtureJson("logoutUser.401.json").get("message"), error.get("message"));
    }

    @Test
    void logoutUser401WithoutAuthorizationHeader() throws Exception {
        JsonNode error = errorBody(mvc.perform(post("/api/v1/auth/logout")), 401);

        assertMatchesFixture(error, "logoutUser.401.json");
        assertEquals(fixtureJson("logoutUser.401.json").get("message"), error.get("message"));
    }

    // ── helpers ─────────────────────────────────────────────────

    private ResultActions postJson(String uri, String body) throws Exception {
        return mvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static JsonNode errorBody(ResultActions result, int expectedStatus) throws Exception {
        MvcResult mvcResult = result.andExpect(status().is(expectedStatus)).andReturn();
        return MAPPER.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Mismas claves que el fixture, mismo status y mismo código de error. */
    private static void assertMatchesFixture(JsonNode actual, String fixtureName) throws IOException {
        JsonNode expected = fixtureJson(fixtureName);
        assertEquals(fieldNames(expected), fieldNames(actual), "claves distintas de " + fixtureName);
        assertEquals(expected.get("error"), actual.get("error"));
        assertEnvelope(actual, expected.get("status").asInt());
    }

    private static void assertEnvelope(JsonNode actual, int status) {
        assertEquals(status, actual.get("status").asInt());
        assertTrue(!actual.get("message").asText().isBlank());
        String timestamp = actual.get("timestamp").asText();
        assertTrue(timestamp.endsWith("Z"), "timestamp sin Z: " + timestamp);
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name));
    }

    private static JsonNode fixtureJson(String name) throws IOException {
        return MAPPER.readTree(fixture(name));
    }
}
