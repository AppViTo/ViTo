package com.vito.mosaic.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MosaicFeedItem {
    
    private String id;
    
    private String eventId;
    
    private String userId;
    
    private String username;
    
    private String displayName;
    
    private String avatar;
    
    private FeedItemType type;
    
    private String content;
    
    private List<String> mediaUrls;
    
    private LocalDateTime timestamp;
    
    private int likesCount;
    
    private int commentsCount;
    
    private List<String> likedBy;
    
    private boolean isLiked;
    
    public enum FeedItemType {
        CHECK_IN,
        POST,
        PHOTO,
        REACTION,
        STORY,
        LIVE_UPDATE,
        ANNOUNCEMENT
    }
    
    public void like(String userId) {
        if (!likedBy.contains(userId)) {
            likedBy.add(userId);
            likesCount++;
            isLiked = true;
        }
    }
    
    public void unlike(String userId) {
        if (likedBy.remove(userId)) {
            likesCount--;
            isLiked = false;
        }
    }
}
