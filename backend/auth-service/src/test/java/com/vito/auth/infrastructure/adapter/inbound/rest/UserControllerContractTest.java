package com.vito.auth.infrastructure.adapter.inbound.rest;

import com.vito.auth.application.service.UserDirectoryServiceImpl;
import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.outbound.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T061 (Ola 2a, spec 004): {@code getUsersByIds} comparado con docs/api/fixtures/users. Usa el
 * servicio real con el repositorio simulado, así que también prueba el orden, los omitidos y los
 * repetidos (research R1).
 */
class UserControllerContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "users");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final User CARO = User.builder()
            .id("usr_01H8X2K4QV").username("caro.mz").email("caro@vito.com").passwordHash("$2a$secret")
            .profile(User.Profile.builder().displayName("Carolina Martínez")
                    .avatar("https://cdn.vito.com/usr_01H8X2K4QV/avatar.jpg").bio("no va en UserSummary").build())
            .build();
    /** Perfil privado: getUsersByIds lo incluye (la privacidad solo aplica a searchUsers, FR-004). */
    private static final User LUIS = User.builder()
            .id("usr_01H8X2K4QL").username("luis").email("luis@vito.com").passwordHash("$2a$secret")
            .profile(User.Profile.builder().displayName("Luis").build())
            .settings(User.Settings.builder().privateProfile(true).build())
            .build();
    /** Sin perfil: displayName y avatar salen en null, no se omiten. */
    private static final User ANDRES = User.builder()
            .id("usr_01H8X2K4QW").username("andres.r").email("andres@vito.com").build();

    private final UserRepository userRepository = mock(UserRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(new UserDirectoryServiceImpl(userRepository)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authenticate();
        // El repositorio devuelve en cualquier orden y solo los que existen
        when(userRepository.findAllByIds(any())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            List<User> found = new ArrayList<>(List.of(ANDRES, LUIS, CARO));
            found.removeIf(user -> !ids.contains(user.getId()));
            return found;
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUsersByIds200IsTheFixtureInTheRequestedOrder() throws Exception {
        JsonNode body = okBody(getUsers("usr_01H8X2K4QV,usr_01H8X2K4QL"));

        assertEquals(fixtureJson("getUsersByIds.200.json"), body);
    }

    @Test
    void orderFollowsTheRequestNotTheRepository() throws Exception {
        JsonNode body = okBody(getUsers("usr_01H8X2K4QL,usr_01H8X2K4QW,usr_01H8X2K4QV"));

        assertEquals(List.of("usr_01H8X2K4QL", "usr_01H8X2K4QW", "usr_01H8X2K4QV"), userIds(body));
    }

    @Test
    void unknownIdsAreOmitted() throws Exception {
        JsonNode body = okBody(getUsers("usr_no_existe,usr_01H8X2K4QV,otro_que_no"));

        assertEquals(List.of("usr_01H8X2K4QV"), userIds(body));
    }

    @Test
    void onlyUnknownIdsIsAnEmptyArray() throws Exception {
        JsonNode body = okBody(getUsers("usr_no_existe"));

        assertTrue(body.isArray());
        assertEquals(0, body.size());
    }

    @Test
    void privateProfilesAreIncluded() throws Exception {
        JsonNode body = okBody(getUsers("usr_01H8X2K4QL"));

        assertEquals(List.of("usr_01H8X2K4QL"), userIds(body));
    }

    @Test
    void repeatedIdsAreReturnedOnce() throws Exception {
        JsonNode body = okBody(getUsers("usr_01H8X2K4QV,usr_01H8X2K4QL,usr_01H8X2K4QV"));

        assertEquals(List.of("usr_01H8X2K4QV", "usr_01H8X2K4QL"), userIds(body));
    }

    @Test
    void missingProfileFieldsAreNullNotAbsent() throws Exception {
        JsonNode user = okBody(getUsers("usr_01H8X2K4QW")).get(0);

        assertEquals(Set.of("userId", "username", "displayName", "avatar"), fieldNames(user));
        assertTrue(user.get("displayName").isNull());
        assertTrue(user.get("avatar").isNull());
    }

    @Test
    void responsesNeverCarryEmailNorPhone() throws Exception {
        String raw = getUsers("usr_01H8X2K4QV,usr_01H8X2K4QL,usr_01H8X2K4QW")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(raw.contains("email"), raw);
        assertFalse(raw.contains("phone"), raw);
        assertFalse(raw.contains("@vito.com"), raw);
        assertFalse(raw.contains("passwordHash"), raw);
        assertFalse(raw.contains("bio"), raw);
    }

    @Test
    void fiftyIdsAreAccepted() throws Exception {
        getUsers(ids(50)).andExpect(status().isOk());
    }

    // ── 400 ─────────────────────────────────────────────────────

    @Test
    void getUsersByIds400WithFiftyOneIds() throws Exception {
        assertValidationError(getUsers(ids(51)));
    }

    /** maxItems cuenta la lista enviada: 51 ids con repetidos también es 400. */
    @Test
    void getUsersByIds400WithFiftyOneIdsEvenIfRepeated() throws Exception {
        String repeated = IntStream.range(0, 51).mapToObj(i -> "usr_01H8X2K4QV").collect(Collectors.joining(","));

        assertValidationError(getUsers(repeated));
    }

    @Test
    void getUsersByIds400WithoutIds() throws Exception {
        assertValidationError(mvc.perform(get("/api/v1/users")));
    }

    @Test
    void getUsersByIds400WithEmptyIds() throws Exception {
        assertValidationError(getUsers(""));
    }

    @Test
    void getUsersByIds400WithOnlyCommasAndSpaces() throws Exception {
        assertValidationError(getUsers(" , ,"));
    }

    // ── 401 ─────────────────────────────────────────────────────

    @Test
    void getUsersByIds401WithoutSession() throws Exception {
        SecurityContextHolder.clearContext();

        assertUnauthorized(getUsers("usr_01H8X2K4QV"));
    }

    @Test
    void getUsersByIds401ForTheAnonymousUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertUnauthorized(getUsers("usr_01H8X2K4QV"));
    }

    // ── helpers ─────────────────────────────────────────────────

    private void assertValidationError(ResultActions result) throws Exception {
        JsonNode error = errorBody(result, 400);

        assertMatchesFixture(error, "getUsersByIds.400.json");
        assertEquals(fixtureJson("getUsersByIds.400.json").get("message"), error.get("message"));
        assertEquals(fixtureJson("getUsersByIds.400.json").get("details"), error.get("details"));
        verify(userRepository, never()).findAllByIds(any());
    }

    private void assertUnauthorized(ResultActions result) throws Exception {
        JsonNode error = errorBody(result, 401);

        assertMatchesFixture(error, "getUsersByIds.401.json");
        assertEquals(fixtureJson("getUsersByIds.401.json").get("message"), error.get("message"));
        verify(userRepository, never()).findAllByIds(any());
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usr_01H8X2K4QJ", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private ResultActions getUsers(String ids) throws Exception {
        return mvc.perform(get("/api/v1/users").param("ids", ids));
    }

    private static String ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> "usr_" + i).collect(Collectors.joining(","));
    }

    private static JsonNode okBody(ResultActions result) throws Exception {
        MvcResult mvcResult = result.andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode errorBody(ResultActions result, int expectedStatus) throws Exception {
        MvcResult mvcResult = result.andExpect(status().is(expectedStatus)).andReturn();
        return MAPPER.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> userIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.forEach(user -> ids.add(user.get("userId").asText()));
        return ids;
    }

    private static void assertMatchesFixture(JsonNode actual, String fixtureName) throws IOException {
        JsonNode expected = fixtureJson(fixtureName);
        assertEquals(fieldNames(expected), fieldNames(actual), "claves distintas de " + fixtureName);
        assertEquals(expected.get("error"), actual.get("error"));
        assertEquals(expected.get("status").asInt(), actual.get("status").asInt());
        String timestamp = actual.get("timestamp").asText();
        assertTrue(timestamp.endsWith("Z"), "timestamp sin Z: " + timestamp);
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private static JsonNode fixtureJson(String name) throws IOException {
        return MAPPER.readTree(Files.readString(FIXTURES.resolve(name)));
    }
}
