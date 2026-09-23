package com.vito.mosaic.domain.port.outbound;

import com.vito.mosaic.domain.model.MosaicFeedItem;

import java.util.List;
import java.util.Optional;

public interface MosaicFeedRepository {
    
    MosaicFeedItem save(MosaicFeedItem item);
    
    Optional<MosaicFeedItem> findById(String itemId);
    
    List<MosaicFeedItem> findByEventId(String eventId, int page, int size);
    
    List<MosaicFeedItem> findByEventIdAndType(String eventId, MosaicFeedItem.FeedItemType type, int page, int size);
    
    long countByEventId(String eventId);
    
    void delete(String itemId);
    
    void like(String itemId, String userId);
    
    void unlike(String itemId, String userId);
}
