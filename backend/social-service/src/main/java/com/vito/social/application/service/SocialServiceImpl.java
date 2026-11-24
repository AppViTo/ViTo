package com.vito.social.application.service;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.model.UserSummary;
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
    
    /**
     * T045 (research R6, data-model.md): identidad de auth-service, contadores con {@code count} y
     * las dos direcciones del grafo. En el propio perfil los dos booleanos son {@code false}.
     */
    @Override
    public SocialProfile getSocialProfile(String userId, String currentUserId) {
        UserSummary user = userDirectory.findByIds(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(SocialException::userNotFound);

        boolean own = userId.equals(currentUserId);
        return SocialProfile.builder()
                .userId(user.userId())
                .username(user.username())
                .displayName(user.displayName())
                .avatar(user.avatar())
                .followersCount(Math.toIntExact(followRepository.countByFollowingId(userId)))
                .followingCount(Math.toIntExact(followRepository.countByFollowerId(userId)))
                .isFollowing(!own && followRepository.existsByFollowerIdAndFollowingId(currentUserId, userId))
                .isFollowedBy(!own && followRepository.existsByFollowerIdAndFollowingId(userId, currentUserId))
                .build();
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
