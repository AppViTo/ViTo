package com.vito.social.domain.port.outbound;

public interface EventPublisherPort {
    
    void publishFollowEvent(String followerId, String followingId);
    
    void publishUnfollowEvent(String followerId, String followingId);
}
