package com.vito.social.application.service;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.port.inbound.SocialService;
import com.vito.social.domain.port.outbound.EventPublisherPort;
import com.vito.social.domain.port.outbound.FollowRepository;
import com.vito.social.domain.port.outbound.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialServiceImpl implements SocialService {
    
    private final FollowRepository followRepository;
    private final UserProfileRepository userProfileRepository;
    private final EventPublisherPort eventPublisher;
    
    @Override
    public void follow(FollowCommand command) {
        String followerId = command.getFollowerId();
        String followingId = command.getFollowingId();
        
        log.info("User {} attempting to follow {}", followerId, followingId);
        
        // Validate not following yourself
        if (followerId.equals(followingId)) {
            throw SocialException.cannotFollowYourself();
        }
        
        // Validate not already following
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw SocialException.alreadyFollowing();
        }
        
        // Create follow relationship
        FollowRelationship relationship = FollowRelationship.create(followerId, followingId);
        followRepository.save(relationship);
        
        // Update counters
        userProfileRepository.incrementFollowingCount(followerId);
        userProfileRepository.incrementFollowersCount(followingId);
        
        // Publish event
        eventPublisher.publishFollowEvent(followerId, followingId);
        
        log.info("User {} successfully followed {}", followerId, followingId);
    }
    
    @Override
    public void unfollow(UnfollowCommand command) {
        String followerId = command.getFollowerId();
        String followingId = command.getFollowingId();
        
        log.info("User {} attempting to unfollow {}", followerId, followingId);
        
        // Validate is following
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw SocialException.notFollowing();
        }
        
        // Delete follow relationship
        FollowRelationship relationship = FollowRelationship.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build();
        followRepository.delete(relationship);
        
        // Update counters
        userProfileRepository.decrementFollowingCount(followerId);
        userProfileRepository.decrementFollowersCount(followingId);
        
        // Publish event
        eventPublisher.publishUnfollowEvent(followerId, followingId);
        
        log.info("User {} successfully unfollowed {}", followerId, followingId);
    }
    
    @Override
    public boolean isFollowing(String followerId, String followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }
    
    @Override
    public SocialProfile getSocialProfile(String userId, String currentUserId) {
        SocialProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(SocialException::userNotFound);
        
        // Set follow status
        if (currentUserId != null && !currentUserId.equals(userId)) {
            profile.setFollowing(isFollowing(currentUserId, userId));
            profile.setFollowedBy(isFollowing(userId, currentUserId));
        }
        
        return profile;
    }
    
    @Override
    public List<SocialProfile> getFollowers(String userId, int page, int size) {
        List<String> followerIds = followRepository.findFollowerIdsByFollowingId(userId);
        
        if (followerIds.isEmpty()) {
            return List.of();
        }
        
        List<SocialProfile> profiles = userProfileRepository.findByUserIds(followerIds);
        
        return profiles.stream()
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<SocialProfile> getFollowing(String userId, int page, int size) {
        List<String> followingIds = followRepository.findFollowingIdsByFollowerId(userId);
        
        if (followingIds.isEmpty()) {
            return List.of();
        }
        
        List<SocialProfile> profiles = userProfileRepository.findByUserIds(followingIds);
        
        return profiles.stream()
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<SocialProfile> searchUsers(String query, int page, int size) {
        // This would typically use a search service or MongoDB text search
        // For now, we'll return an empty list
        log.info("Searching users with query: {}", query);
        return List.of();
    }
}
