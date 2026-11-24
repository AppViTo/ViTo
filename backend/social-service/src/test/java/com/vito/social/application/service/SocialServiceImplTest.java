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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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

    private SocialProfile testProfile;

    @BeforeEach
    void setUp() {
        testProfile = SocialProfile.builder()
                .userId("user123")
                .username("testuser")
                .displayName("Test User")
                .avatar("https://example.com/avatar.jpg")
                .bio("Test bio")
                .followersCount(100)
                .followingCount(50)
                .build();
    }

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

    @Test
    void getSocialProfile_Success() {
        // Arrange
        String userId = "user123";
        String currentUserId = "currentUser";

        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(testProfile));
        when(followRepository.existsByFollowerIdAndFollowingId(currentUserId, userId)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingId(userId, currentUserId)).thenReturn(false);

        // Act
        SocialProfile result = socialService.getSocialProfile(userId, currentUserId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertTrue(result.isFollowing());
        assertFalse(result.isFollowedBy());

        verify(userProfileRepository).findByUserId(userId);
        verify(followRepository).existsByFollowerIdAndFollowingId(currentUserId, userId);
        verify(followRepository).existsByFollowerIdAndFollowingId(userId, currentUserId);
    }

    @Test
    void getSocialProfile_UserNotFound_ThrowsException() {
        // Arrange
        String userId = "nonexistent";
        String currentUserId = "currentUser";

        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        SocialException exception = assertThrows(
                SocialException.class,
                () -> socialService.getSocialProfile(userId, currentUserId)
        );
        assertEquals("El usuario no existe", exception.getMessage());

        verify(userProfileRepository).findByUserId(userId);
    }

    @Test
    void isFollowing_ReturnsTrue() {
        // Arrange
        String followerId = "follower123";
        String followingId = "following123";

        when(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
                .thenReturn(true);

        // Act
        boolean result = socialService.isFollowing(followerId, followingId);

        // Assert
        assertTrue(result);
        verify(followRepository).existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    @Test
    void isFollowing_ReturnsFalse() {
        // Arrange
        String followerId = "follower123";
        String followingId = "following123";

        when(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
                .thenReturn(false);

        // Act
        boolean result = socialService.isFollowing(followerId, followingId);

        // Assert
        assertFalse(result);
        verify(followRepository).existsByFollowerIdAndFollowingId(followerId, followingId);
    }
}
