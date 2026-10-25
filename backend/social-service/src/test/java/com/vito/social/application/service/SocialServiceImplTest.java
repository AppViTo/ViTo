package com.vito.social.application.service;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.port.inbound.SocialService.FollowCommand;
import com.vito.social.domain.port.inbound.SocialService.UnfollowCommand;
import com.vito.social.domain.port.outbound.EventPublisherPort;
import com.vito.social.domain.port.outbound.FollowRepository;
import com.vito.social.domain.port.outbound.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void follow_Success() {
        // Arrange
        FollowCommand command = FollowCommand.builder()
                .followerId("follower123")
                .followingId("following123")
                .build();

        when(followRepository.existsByFollowerIdAndFollowingId("follower123", "following123"))
                .thenReturn(false);

        // Act
        socialService.follow(command);

        // Assert
        verify(followRepository).existsByFollowerIdAndFollowingId("follower123", "following123");
        verify(followRepository).save(any(FollowRelationship.class));
        verify(userProfileRepository).incrementFollowingCount("follower123");
        verify(userProfileRepository).incrementFollowersCount("following123");
        verify(eventPublisher).publishFollowEvent("follower123", "following123");
    }

    @Test
    void follow_CannotFollowYourself_ThrowsException() {
        // Arrange
        FollowCommand command = FollowCommand.builder()
                .followerId("user123")
                .followingId("user123")
                .build();

        // Act & Assert
        SocialException exception = assertThrows(
                SocialException.class,
                () -> socialService.follow(command)
        );
        assertEquals("No puedes seguirte a ti mismo", exception.getMessage());

        verify(followRepository, never()).existsByFollowerIdAndFollowingId(any(), any());
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_AlreadyFollowing_ThrowsException() {
        // Arrange
        FollowCommand command = FollowCommand.builder()
                .followerId("follower123")
                .followingId("following123")
                .build();

        when(followRepository.existsByFollowerIdAndFollowingId("follower123", "following123"))
                .thenReturn(true);

        // Act & Assert
        SocialException exception = assertThrows(
                SocialException.class,
                () -> socialService.follow(command)
        );
        assertEquals("Ya sigues a este usuario", exception.getMessage());

        verify(followRepository).existsByFollowerIdAndFollowingId("follower123", "following123");
        verify(followRepository, never()).save(any());
    }

    @Test
    void unfollow_Success() {
        // Arrange
        UnfollowCommand command = UnfollowCommand.builder()
                .followerId("follower123")
                .followingId("following123")
                .build();

        when(followRepository.existsByFollowerIdAndFollowingId("follower123", "following123"))
                .thenReturn(true);

        // Act
        socialService.unfollow(command);

        // Assert
        verify(followRepository).existsByFollowerIdAndFollowingId("follower123", "following123");
        verify(followRepository).delete(any(FollowRelationship.class));
        verify(userProfileRepository).decrementFollowingCount("follower123");
        verify(userProfileRepository).decrementFollowersCount("following123");
        verify(eventPublisher).publishUnfollowEvent("follower123", "following123");
    }

    @Test
    void unfollow_NotFollowing_ThrowsException() {
        // Arrange
        UnfollowCommand command = UnfollowCommand.builder()
                .followerId("follower123")
                .followingId("following123")
                .build();

        when(followRepository.existsByFollowerIdAndFollowingId("follower123", "following123"))
                .thenReturn(false);

        // Act & Assert
        SocialException exception = assertThrows(
                SocialException.class,
                () -> socialService.unfollow(command)
        );
        assertEquals("No sigues a este usuario", exception.getMessage());

        verify(followRepository).existsByFollowerIdAndFollowingId("follower123", "following123");
        verify(followRepository, never()).delete(any());
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
