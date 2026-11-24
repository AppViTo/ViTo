package com.vito.social.infrastructure.adapter.inbound.rest;

import com.vito.social.application.service.SocialServiceImpl;
import com.vito.social.domain.exception.UserDirectoryUnavailableException;
import com.vito.social.domain.model.UserSummary;
import com.vito.social.domain.port.outbound.EventPublisherPort;
import com.vito.social.domain.port.outbound.FollowRepository;
import com.vito.social.domain.port.outbound.UserDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T038, T044 y T050 (Ola 2a): {@code followUser}, {@code unfollowUser}, {@code getSocialProfile}, {@code listFollowers} y {@code listFollowing} contra sus fixtures de
 * {@code docs/api/fixtures/social/}. Controlador, servicio y handler reales; solo el grafo,
 * auth-service y Kafka son dobles.
 */
class SocialControllerContractTest {

    private static final String ME = "usr_ana";
    private static final String JOSE = "usr_jose";
    private static final UserSummary JOSE_SUMMARY = new UserSummary(JOSE, "jose", "José Pérez", null);

    private final FollowRepository follows = mock(FollowRepository.class);
    private final UserDirectory directory = mock(UserDirectory.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SocialServiceImpl service = new SocialServiceImpl(follows, mock(EventPublisherPort.class), directory);
        mvc = MockMvcBuilders.standaloneSetup(new SocialController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TestingAuthenticationToken user = new TestingAuthenticationToken(ME, null);
        user.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(user);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── followUser ─────────────────────────────────────────────────────────────

    @Test
    void followIs204WithoutBody() throws Exception {
        assertTrue(fixture("followUser.204.json").isNull());
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.insertIfAbsent(any())).thenReturn(true);

        mvc.perform(post("/api/v1/social/follow/" + JOSE))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void followingAgainIsAlso204() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.insertIfAbsent(any())).thenReturn(false);

        mvc.perform(post("/api/v1/social/follow/" + JOSE))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void followingYourselfMatchesFixture400() throws Exception {
        assertMatchesFixture(mvc.perform(post("/api/v1/social/follow/" + ME)), "followUser.self.400.json");
    }

    @Test
    void followingAMissingUserMatchesFixture404() throws Exception {
        when(directory.findByIds(List.of("usr_ghost"))).thenReturn(List.of());

        assertMatchesFixture(mvc.perform(post("/api/v1/social/follow/usr_ghost")), "followUser.404.json");
    }

    @Test
    void followWithAuthServiceDownMatchesFixture503() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenThrow(new UserDirectoryUnavailableException());

        assertMatchesFixture(mvc.perform(post("/api/v1/social/follow/" + JOSE)), "followUser.503.json")
                .andExpect(header().string("Retry-After", "5"));
    }

    // ── unfollowUser ───────────────────────────────────────────────────────────

    @Test
    void unfollowIs204WithoutBody() throws Exception {
        assertTrue(fixture("unfollowUser.204.json").isNull());
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.deleteIfPresent(ME, JOSE)).thenReturn(true);

        mvc.perform(delete("/api/v1/social/follow/" + JOSE))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void unfollowingSomeoneYouDoNotFollowIsAlso204() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.deleteIfPresent(ME, JOSE)).thenReturn(false);

        mvc.perform(delete("/api/v1/social/follow/" + JOSE))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void unfollowingAMissingUserMatchesFixture404() throws Exception {
        when(directory.findByIds(List.of("usr_ghost"))).thenReturn(List.of());

        assertMatchesFixture(mvc.perform(delete("/api/v1/social/follow/usr_ghost")), "unfollowUser.404.json");
    }

    @Test
    void unfollowWithAuthServiceDownMatchesFixture503() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenThrow(new UserDirectoryUnavailableException());

        assertMatchesFixture(mvc.perform(delete("/api/v1/social/follow/" + JOSE)), "unfollowUser.503.json")
                .andExpect(header().string("Retry-After", "5"));
    }

    // ── getSocialProfile (T044) ────────────────────────────────────────────────

    @Test
    void profileOfSomeoneElseMatchesFixture200() throws Exception {
        JsonNode expected = fixture("getSocialProfile.other.200.json");
        String caro = expected.get("userId").asText();
        when(directory.findByIds(List.of(caro))).thenReturn(List.of(new UserSummary(caro,
                expected.get("username").asText(), expected.get("displayName").asText(),
                expected.get("avatar").asText())));
        when(follows.countByFollowingId(caro)).thenReturn(2L);
        when(follows.countByFollowerId(caro)).thenReturn(1L);
        when(follows.existsByFollowerIdAndFollowingId(ME, caro)).thenReturn(true);
        when(follows.existsByFollowerIdAndFollowingId(caro, ME)).thenReturn(true);

        String body = mvc.perform(get("/api/v1/social/profile/" + caro))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(expected, new ObjectMapper().readTree(body));
    }

    @Test
    void ownProfileMatchesFixture200() throws Exception {
        JsonNode expected = fixture("getSocialProfile.own.200.json");
        when(directory.findByIds(List.of(ME))).thenReturn(List.of(new UserSummary(ME,
                expected.get("username").asText(), expected.get("displayName").asText(),
                expected.get("avatar").asText())));
        when(follows.countByFollowingId(ME)).thenReturn(1L);
        when(follows.countByFollowerId(ME)).thenReturn(2L);

        String body = mvc.perform(get("/api/v1/social/profile/" + ME))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actual = new ObjectMapper().readTree(body);
        assertEquals(expected.get("followersCount"), actual.get("followersCount"));
        assertEquals(expected.get("followingCount"), actual.get("followingCount"));
        assertFalse(actual.get("isFollowing").asBoolean());
        assertFalse(actual.get("isFollowedBy").asBoolean());
    }

    @Test
    void profileOfAMissingUserMatchesFixture404() throws Exception {
        when(directory.findByIds(List.of("usr_ghost"))).thenReturn(List.of());

        assertMatchesFixture(mvc.perform(get("/api/v1/social/profile/usr_ghost")), "getSocialProfile.404.json");
    }

    @Test
    void profileWithAuthServiceDownMatchesFixture503() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenThrow(new UserDirectoryUnavailableException());

        assertMatchesFixture(mvc.perform(get("/api/v1/social/profile/" + JOSE)), "getSocialProfile.503.json")
                .andExpect(header().string("Retry-After", "5"));
    }

    /** T047: {@code isFollowing} va dentro del perfil; la ruta suelta nunca estuvo en el contrato. */
    @Test
    void isFollowingRouteNoLongerExists() throws Exception {
        mvc.perform(get("/api/v1/social/is-following/" + JOSE))
                .andExpect(status().isNotFound());
    }

    // ── listFollowers y listFollowing (T050) ───────────────────────────────────

    private static final UserSummary ALEX = new UserSummary("usr_01H8X2K4QJ", "alex_villa", "Alex Villa",
            "https://cdn.vito.com/usr_01H8X2K4QJ/avatar.jpg");
    private static final UserSummary LUIS = new UserSummary("usr_01H8X2K4QL", "luis", "Luis", null);

    @Test
    void followersPageMatchesFixture200() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.findFollowerIds(JOSE, 0, 20)).thenReturn(List.of(ALEX.userId(), LUIS.userId()));
        when(directory.findByIds(List.of(ALEX.userId(), LUIS.userId()))).thenReturn(List.of(ALEX, LUIS));

        assertBodyMatchesFixture(mvc.perform(get("/api/v1/social/followers/" + JOSE)), "listFollowers.page.200.json");
    }

    @Test
    void followingMatchesFixture200() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.findFollowingIds(JOSE, 0, 20)).thenReturn(List.of(ALEX.userId()));
        when(directory.findByIds(List.of(ALEX.userId()))).thenReturn(List.of(ALEX));

        assertBodyMatchesFixture(mvc.perform(get("/api/v1/social/following/" + JOSE)), "listFollowing.200.json");
    }

    @Test
    void noFollowersIsAnEmptyArray() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.findFollowerIds(JOSE, 3, 20)).thenReturn(List.of());

        assertBodyMatchesFixture(mvc.perform(get("/api/v1/social/followers/" + JOSE).param("page", "3")),
                "listFollowers.empty.200.json");
    }

    /** FR-012a: un id que auth-service ya no devuelve se omite; la página trae menos que size. */
    @Test
    void aFollowerThatAuthServiceNoLongerReturnsIsOmitted() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenReturn(List.of(JOSE_SUMMARY));
        when(follows.findFollowerIds(JOSE, 0, 20)).thenReturn(List.of(ALEX.userId(), "usr_borrado", LUIS.userId()));
        when(directory.findByIds(List.of(ALEX.userId(), "usr_borrado", LUIS.userId()))).thenReturn(List.of(ALEX, LUIS));

        assertBodyMatchesFixture(mvc.perform(get("/api/v1/social/followers/" + JOSE)), "listFollowers.page.200.json");
    }

    @Test
    void sizeOf51MatchesFixture400() throws Exception {
        assertMatchesFixture(mvc.perform(get("/api/v1/social/followers/" + JOSE).param("size", "51")),
                "listFollowers.400.json")
                .andExpect(jsonPath("$.details.size").value(fixture("listFollowers.400.json").get("details").get("size").asText()));
        assertMatchesFixture(mvc.perform(get("/api/v1/social/following/" + JOSE).param("size", "0")),
                "listFollowing.400.json");
    }

    @Test
    void negativePageIs400WithPageDetail() throws Exception {
        assertMatchesFixture(mvc.perform(get("/api/v1/social/following/" + JOSE).param("page", "-1")),
                "listFollowing.400.json")
                .andExpect(jsonPath("$.details.page").isNotEmpty());
    }

    @Test
    void listsOfAMissingUserMatchFixture404() throws Exception {
        when(directory.findByIds(List.of("usr_ghost"))).thenReturn(List.of());

        assertMatchesFixture(mvc.perform(get("/api/v1/social/followers/usr_ghost")), "listFollowers.404.json");
        assertMatchesFixture(mvc.perform(get("/api/v1/social/following/usr_ghost")), "listFollowing.404.json");
    }

    @Test
    void listsWithAuthServiceDownMatchFixture503() throws Exception {
        when(directory.findByIds(List.of(JOSE))).thenThrow(new UserDirectoryUnavailableException());

        assertMatchesFixture(mvc.perform(get("/api/v1/social/followers/" + JOSE)), "listFollowers.503.json")
                .andExpect(header().string("Retry-After", "5"));
        assertMatchesFixture(mvc.perform(get("/api/v1/social/following/" + JOSE)), "listFollowing.503.json");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Mismo {@code status}, {@code error} y {@code message} que el fixture; el timestamp es el real. */
    private static ResultActions assertMatchesFixture(ResultActions result, String fixtureName) throws Exception {
        JsonNode expected = fixture(fixtureName);
        return result.andExpect(status().is(expected.get("status").asInt()))
                .andExpect(jsonPath("$.status").value(expected.get("status").asInt()))
                .andExpect(jsonPath("$.error").value(expected.get("error").asText()))
                .andExpect(jsonPath("$.message").value(expected.get("message").asText()))
                .andExpect(jsonPath("$.timestamp", endsWith("Z")));
    }

    /** El cuerpo es el del fixture campo por campo (los null incluidos). */
    private static void assertBodyMatchesFixture(ResultActions result, String fixtureName) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(fixture(fixtureName), new ObjectMapper().readTree(body));
    }

    private static JsonNode fixture(String name) throws Exception {
        String json = Files.readString(Path.of("..", "..", "docs", "api", "fixtures", "social", name));
        return new ObjectMapper().readTree(json);
    }
}
