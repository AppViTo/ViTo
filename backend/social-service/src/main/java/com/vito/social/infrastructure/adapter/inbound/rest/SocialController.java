package com.vito.social.infrastructure.adapter.inbound.rest;

import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.port.inbound.SocialService;
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
    
    @PostMapping("/follow/{userId}")
    public ResponseEntity<Void> follow(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        socialService.follow(SocialService.FollowCommand.builder()
                .followerId(currentUserId)
                .followingId(userId)
                .build());
        
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<Void> unfollow(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        socialService.unfollow(SocialService.UnfollowCommand.builder()
                .followerId(currentUserId)
                .followingId(userId)
                .build());
        
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/profile/{userId}")
    public ResponseEntity<SocialProfile> getProfile(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        SocialProfile profile = socialService.getSocialProfile(userId, currentUserId);
        
        return ResponseEntity.ok(profile);
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
    
    @GetMapping("/search")
    public ResponseEntity<List<SocialProfile>> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<SocialProfile> users = socialService.searchUsers(query, page, size);
        
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/is-following/{userId}")
    public ResponseEntity<Boolean> isFollowing(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        
        boolean isFollowing = socialService.isFollowing(currentUserId, userId);
        
        return ResponseEntity.ok(isFollowing);
    }
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
