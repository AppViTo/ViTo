package com.vito.mosaic.domain.port.outbound;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicItem;

public interface MosaicEventPublisher {
    
    void publishNewItem(String eventId, MosaicFeedItem item);
    
    void publishGridUpdate(String eventId, MosaicItem item, int position);
    
    void publishLikeUpdate(String eventId, String itemId, int likesCount);

    /** El organizador retiró un ítem (T-B26). */
    void publishItemRemoved(String eventId, String itemId);
}
