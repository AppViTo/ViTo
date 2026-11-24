package com.vito.social.application.service;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.exception.UserDirectoryUnavailableException;
import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.model.UserSummary;
import com.vito.social.domain.port.inbound.SocialService.FollowCommand;
import com.vito.social.domain.port.inbound.SocialService.UnfollowCommand;
import com.vito.social.domain.port.outbound.EventPublisherPort;
import com.vito.social.domain.port.outbound.FollowRepository;
import com.vito.social.domain.port.outbound.UserDirectory;
import com.vito.social.domain.port.outbound.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private SocialServiceImpl socialService;

    // ── T037 (research R5): follow y unfollow idempotentes ─────────────────────

    private static final UserSummary JOSE = new UserSummary("following123", "jose", "José Pérez", null);

    private static FollowCommand follow(String follower, String following) {
        return FollowCommand.builder().followerId(follower).followingId(following).build();
    }

    private static UnfollowCommand unfollow(String follower, String following) {
        return UnfollowCommand.builder().followerId(follower).followingId(following).build();
    }

    @Test
    void followCreatesTheRelationshipAndPublishesOneEvent() {
        when(userDirectory.findByIds(List.of("following123"))).thenReturn(List.of(JOSE));
        when(followRepository.insertIfAbsent(any(FollowRelationship.class))).thenReturn(true);

        socialService.follow(follow("follower123", "following123"));

        ArgumentCaptor<FollowRelationship> saved = ArgumentCaptor.forClass(FollowRelationship.class);
        verify(followRepository).insertIfAbsent(saved.capture());
        assertEquals("follower123", saved.getValue().getFollowerId());
        assertEquals("following123", saved.getValue().getFollowingId());
        assertNotNull(saved.getValue().getCreatedAt());
        verify(eventPublisher).publishFollowEvent("follower123", "following123");
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void followingAgainIsNotAnErrorAndPublishesNothing() {
        when(userDirectory.findByIds(List.of("following123"))).thenReturn(List.of(JOSE));
        when(followRepository.insertIfAbsent(any(FollowRelationship.class))).thenReturn(false);

        assertDoesNotThrow(() -> socialService.follow(follow("follower123", "following123")));

        verifyNoInteractions(eventPublisher, userProfileRepository);
    }

    @Test
    void followingYourselfIs400WithoutTouchingTheGraph() {
        SocialException ex = assertThrows(SocialException.class,
                () -> socialService.follow(follow("user123", "user123")));

        assertEquals(SocialException.Reason.INVALID, ex.getReason());
        assertEquals("No puedes seguirte a ti mismo", ex.getMessage());
        verifyNoInteractions(followRepository, userDirectory, eventPublisher);
    }

    @Test
    void followingAUserThatAuthServiceDoesNotReturnIs404() {
        when(userDirectory.findByIds(List.of("ghost"))).thenReturn(List.of());

        SocialException ex = assertThrows(SocialException.class,
                () -> socialService.follow(follow("follower123", "ghost")));

        assertEquals(SocialException.Reason.NOT_FOUND, ex.getReason());
        verifyNoInteractions(followRepository, eventPublisher);
    }

    @Test
    void followWithAuthServiceDownPropagatesUnavailable() {
        when(userDirectory.findByIds(List.of("following123"))).thenThrow(new UserDirectoryUnavailableException());

        assertThrows(UserDirectoryUnavailableException.class,
                () -> socialService.follow(follow("follower123", "following123")));

        verifyNoInteractions(followRepository, eventPublisher);
    }

    @Test
    void unfollowRemovesTheRelationshipAndPublishesOneEvent() {
        when(userDirectory.findByIds(List.of("following123"))).thenReturn(List.of(JOSE));
        when(followRepository.deleteIfPresent("follower123", "following123")).thenReturn(true);

        socialService.unfollow(unfollow("follower123", "following123"));

        verify(eventPublisher).publishUnfollowEvent("follower123", "following123");
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void unfollowingSomeoneYouDoNotFollowIsNotAnErrorAndPublishesNothing() {
        when(userDirectory.findByIds(List.of("following123"))).thenReturn(List.of(JOSE));
        when(followRepository.deleteIfPresent("follower123", "following123")).thenReturn(false);

        assertDoesNotThrow(() -> socialService.unfollow(unfollow("follower123", "following123")));

        verifyNoInteractions(eventPublisher, userProfileRepository);
    }

    @Test
    void unfollowingAUserThatAuthServiceDoesNotReturnIs404() {
        when(userDirectory.findByIds(List.of("ghost"))).thenReturn(List.of());

        SocialException ex = assertThrows(SocialException.class,
                () -> socialService.unfollow(unfollow("follower123", "ghost")));

        assertEquals(SocialException.Reason.NOT_FOUND, ex.getReason());
        verify(followRepository, never()).deleteIfPresent(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    // ── T045 (research R6): perfil con identidad de auth-service y contadores calculados ──

    private static final UserSummary CARO =
            new UserSummary("usr_caro", "caro.mz", "Carolina Martínez", "https://cdn.vito.com/caro.jpg");

    @Test
    void profileOfSomeoneElseCombinesIdentityCountsAndBothDirections() {
        when(userDirectory.findByIds(List.of("usr_caro"))).thenReturn(List.of(CARO));
        when(followRepository.countByFollowingId("usr_caro")).thenReturn(2L);
        when(followRepository.countByFollowerId("usr_caro")).thenReturn(1L);
        when(followRepository.existsByFollowerIdAndFollowingId("usr_ana", "usr_caro")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingId("usr_caro", "usr_ana")).thenReturn(false);

        SocialProfile profile = socialService.getSocialProfile("usr_caro", "usr_ana");

        assertEquals("usr_caro", profile.getUserId());
        assertEquals("caro.mz", profile.getUsername());
        assertEquals("Carolina Martínez", profile.getDisplayName());
        assertEquals("https://cdn.vito.com/caro.jpg", profile.getAvatar());
        assertEquals(2, profile.getFollowersCount());
        assertEquals(1, profile.getFollowingCount());
        assertTrue(profile.isFollowing());
        assertFalse(profile.isFollowedBy());
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void ownProfileHasBothBooleansFalseWithoutAskingTheGraph() {
        when(userDirectory.findByIds(List.of("usr_caro"))).thenReturn(List.of(CARO));
        when(followRepository.countByFollowingId("usr_caro")).thenReturn(0L);
        when(followRepository.countByFollowerId("usr_caro")).thenReturn(3L);

        SocialProfile profile = socialService.getSocialProfile("usr_caro", "usr_caro");

        assertFalse(profile.isFollowing());
        assertFalse(profile.isFollowedBy());
        assertEquals(3, profile.getFollowingCount());
        verify(followRepository, never()).existsByFollowerIdAndFollowingId(any(), any());
    }

    @Test
    void profileWithNullDisplayNameAndAvatarKeepsThemNull() {
        when(userDirectory.findByIds(List.of("usr_x"))).thenReturn(List.of(new UserSummary("usr_x", "x", null, null)));

        SocialProfile profile = socialService.getSocialProfile("usr_x", "usr_ana");

        assertNull(profile.getDisplayName());
        assertNull(profile.getAvatar());
    }

    @Test
    void profileOfAUserThatAuthServiceDoesNotReturnIs404() {
        when(userDirectory.findByIds(List.of("ghost"))).thenReturn(List.of());

        SocialException ex = assertThrows(SocialException.class,
                () -> socialService.getSocialProfile("ghost", "usr_ana"));

        assertEquals(SocialException.Reason.NOT_FOUND, ex.getReason());
        assertEquals("El usuario no existe", ex.getMessage());
        verifyNoInteractions(followRepository);
    }

    @Test
    void profileWithAuthServiceDownPropagatesUnavailable() {
        when(userDirectory.findByIds(List.of("usr_caro"))).thenThrow(new UserDirectoryUnavailableException());

        assertThrows(UserDirectoryUnavailableException.class,
                () -> socialService.getSocialProfile("usr_caro", "usr_ana"));
    }
}
