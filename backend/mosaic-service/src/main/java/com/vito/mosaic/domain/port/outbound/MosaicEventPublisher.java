package com.vito.mosaic.domain.port.outbound;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicItem;

import java.util.List;

public interface MosaicEventPublisher {
    
    void publishNewItem(String eventId, MosaicFeedItem item);
    
    void publishGridUpdate(String eventId, MosaicItem item, int position);
    
    void publishLikeUpdate(String eventId, String itemId, int likesCount);

    /**
     * El organizador retiró un ítem (T-B26). Lleva sus {@code mediaUrls} para que media-service
     * borre los archivos (T-B33, decisión 2 de T-C09).
     */
    void publishItemRemoved(String eventId, String itemId, List<String> mediaUrls);
}
