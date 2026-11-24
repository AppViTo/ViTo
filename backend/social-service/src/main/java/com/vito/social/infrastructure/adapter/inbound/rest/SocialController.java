package com.vito.social.infrastructure.adapter.inbound.rest;

import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.port.inbound.SocialService;
import com.vito.social.domain.exception.SocialException;
import com.vito.social.infrastructure.adapter.inbound.rest.dto.SocialProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {
    
    private final SocialService socialService;
    
    /** T041 (followUser): 204 sin cuerpo, también si ya lo seguía (research R5). */
    @PostMapping("/follow/{userId}")
    public ResponseEntity<Void> follow(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        socialService.follow(SocialService.FollowCommand.builder()
                .followerId(currentUserId)
                .followingId(userId)
                .build());
        
        return ResponseEntity.noContent().build();
    }
    
    /** T041 (unfollowUser): 204 sin cuerpo, también si no lo seguía (research R5). */
    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<Void> unfollow(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        socialService.unfollow(SocialService.UnfollowCommand.builder()
                .followerId(currentUserId)
                .followingId(userId)
                .build());
        
        return ResponseEntity.noContent().build();
    }
    
    /** T046 (getSocialProfile): responde el DTO del contrato, no el modelo de dominio. */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<SocialProfileResponse> getProfile(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        SocialProfile profile = socialService.getSocialProfile(userId, currentUserId);
        
        return ResponseEntity.ok(SocialProfileResponse.from(profile));
    }
    
    @GetMapping("/followers/{userId}")
    public ResponseEntity<List<SocialProfile>> getFollowers(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<SocialProfile> followers = socialService.getFollowers(userId, page, size);
        
        return ResponseEntity.ok(followers);
    }
    
    @GetMapping("/following/{userId}")
    public ResponseEntity<List<SocialProfile>> getFollowing(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<SocialProfile> following = socialService.getFollowing(userId, page, size);
        
        return ResponseEntity.ok(following);
    }
    
    // GET /search retirada (T033, Ola 2a): la búsqueda es searchUsers de auth-service (ADR-016)

    // GET /is-following retirada (T047, Ola 2a): isFollowing e isFollowedBy van en el perfil

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw SocialException.unauthenticated();
        }
        return authentication.getName();
    }
}
