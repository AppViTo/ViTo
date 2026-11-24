package com.vito.social.application.service;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.port.inbound.SocialService;
import com.vito.social.domain.port.outbound.EventPublisherPort;
import com.vito.social.domain.port.outbound.FollowRepository;
import com.vito.social.domain.port.outbound.UserDirectory;
import com.vito.social.domain.port.outbound.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UserDirectory userDirectory;
    
    /**
     * T040 (research R5): idempotente. Repetir el follow no es error ni publica evento; el índice
     * único del par lo hace atómico. Los contadores se calculan al leer (R6): no hay {@code $inc}.
     */
    @Override
    public void follow(FollowCommand command) {
        String followerId = command.getFollowerId();
        String followingId = command.getFollowingId();

        if (followerId.equals(followingId)) {
            throw SocialException.cannotFollowYourself();
        }
        requireExistingUser(followingId);

        if (followRepository.insertIfAbsent(FollowRelationship.create(followerId, followingId))) {
            eventPublisher.publishFollowEvent(followerId, followingId);
            log.info("Follow creado");
        }
    }

    /** T040 (research R5): idempotente. Si no lo seguía, no es error ni publica evento. */
    @Override
    public void unfollow(UnfollowCommand command) {
        String followerId = command.getFollowerId();
        String followingId = command.getFollowingId();

        requireExistingUser(followingId);

        if (followRepository.deleteIfPresent(followerId, followingId)) {
            eventPublisher.publishUnfollowEvent(followerId, followingId);
            log.info("Follow eliminado");
        }
    }

    /**
     * El destino existe si auth-service lo devuelve (FR-012). Si auth-service no responde, el
     * adaptador lanza {@code UserDirectoryUnavailableException} y la petición sale con 503.
     */
    private void requireExistingUser(String userId) {
        if (userDirectory.findByIds(List.of(userId)).isEmpty()) {
            throw SocialException.userNotFound();
        }
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
}
