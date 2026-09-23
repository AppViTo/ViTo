package com.vito.mosaic.domain.port.inbound;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.model.MosaicItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface MosaicService {
    
    MosaicFeedItem addPost(AddPostCommand command);
    
    MosaicFeedItem addPhoto(AddPhotoCommand command);
    
    MosaicFeedItem addCheckIn(AddCheckInCommand command);
    
    List<MosaicFeedItem> getEventFeed(String eventId, int page, int size);
    
    MosaicGrid getEventGrid(String eventId);
    
    MosaicGrid initializeGrid(String eventId, int rows, int cols);
    
    void likePost(String itemId, String userId);
    
    void unlikePost(String itemId, String userId);
    
    MosaicFeedItem getFeedItem(String itemId);
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AddPostCommand {
        private String eventId;
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
        private String content;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AddPhotoCommand {
        private String eventId;
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
        private String content;
        private List<String> mediaUrls;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AddCheckInCommand {
        private String eventId;
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
        private String location;
    }
}
