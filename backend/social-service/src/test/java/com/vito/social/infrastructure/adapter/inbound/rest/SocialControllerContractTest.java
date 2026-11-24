package com.vito.social.infrastructure.adapter.inbound.rest;

import com.vito.social.application.service.SocialServiceImpl;
import com.vito.social.domain.exception.UserDirectoryUnavailableException;
import com.vito.social.domain.model.UserSummary;
import com.vito.social.domain.port.outbound.EventPublisherPort;
import com.vito.social.domain.port.outbound.FollowRepository;
import com.vito.social.domain.port.outbound.UserDirectory;
import com.vito.social.domain.port.outbound.UserProfileRepository;
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
 * T038 y T044 (Ola 2a): {@code followUser}, {@code unfollowUser} y {@code getSocialProfile} contra sus fixtures de
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
        SocialServiceImpl service = new SocialServiceImpl(
                follows, mock(UserProfileRepository.class), mock(EventPublisherPort.class), directory);
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

    private static JsonNode fixture(String name) throws Exception {
        String json = Files.readString(Path.of("..", "..", "docs", "api", "fixtures", "social", name));
        return new ObjectMapper().readTree(json);
    }
}
