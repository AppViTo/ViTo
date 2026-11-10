package com.vito.media.infrastructure.adapter.inbound.messaging;

import com.vito.media.domain.port.inbound.MediaService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** T-B33: MOSAIC_ITEM_REMOVED borra los archivos del elemento; el resto de mosaic-events se ignora. */
class MosaicEventsListenerTest {

    private final MediaService mediaService = mock(MediaService.class);
    private final MosaicEventsListener listener = new MosaicEventsListener(mediaService);

    @Test
    void itemRemovedDeletesItsMedia() {
        listener.onMosaicEvent("""
                {"type":"MOSAIC_ITEM_REMOVED","eventId":"evt_1","itemId":"itm_1",
                 "mediaUrls":["https://cdn.vito.com/a.jpg","https://cdn.vito.com/b.jpg"],"timestamp":1}
                """);

        verify(mediaService).deleteMosaicItemMedia("evt_1", List.of("https://cdn.vito.com/a.jpg", "https://cdn.vito.com/b.jpg"));
    }

    @Test
    void otherTypesAndUnreadableMessagesAreIgnored() {
        listener.onMosaicEvent("{\"type\":\"MOSAIC_LIKES_UPDATED\",\"eventId\":\"evt_1\",\"itemId\":\"itm_1\"}");
        listener.onMosaicEvent("no es json");

        verify(mediaService, never()).deleteMosaicItemMedia(any(), any());
    }

    @Test
    void anOldMessageWithoutUrlsDeletesNothing() {
        listener.onMosaicEvent("{\"type\":\"MOSAIC_ITEM_REMOVED\",\"eventId\":\"evt_1\",\"itemId\":\"itm_1\"}");

        verify(mediaService).deleteMosaicItemMedia("evt_1", List.of());
    }
}
