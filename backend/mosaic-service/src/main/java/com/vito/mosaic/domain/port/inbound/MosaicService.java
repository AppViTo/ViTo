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

    /**
     * removeMosaicItem (T-B26, excepción A3): el organizador del evento retira un ítem. Borrado
     * lógico; la celda de la grilla vuelve a {@code null} y los clientes reciben ITEM_REMOVED.
     *
     * @throws com.vito.mosaic.domain.exception.MosaicException NOT_FOUND si no existe o ya se
     *         retiró; FORBIDDEN si {@code userId} no organiza el evento
     */
    void removeItem(String itemId, String userId);
    
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
