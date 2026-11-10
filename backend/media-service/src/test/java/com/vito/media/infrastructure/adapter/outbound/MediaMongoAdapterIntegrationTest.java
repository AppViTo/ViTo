package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.model.Media;
import com.vito.media.support.MongoContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T-B33: listEventMedia ordena por uploadedAt descendente y pagina en la base, sin borrados. */
@DataMongoTest(properties = "media.mosaic-events.listener-enabled=false")
@Import(MediaMongoAdapter.class)
class MediaMongoAdapterIntegrationTest extends MongoContainerTest {

    @Autowired
    private MediaMongoAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void seed() {
        mongoTemplate.dropCollection(Media.class);
        for (int i = 1; i <= 5; i++) {
            adapter.save(media("m" + i, "evt_1", LocalDateTime.of(2026, 10, 16, 22, i), false));
        }
        adapter.save(media("m_deleted", "evt_1", LocalDateTime.of(2026, 10, 16, 23, 0), true));
        adapter.save(media("m_other", "evt_2", LocalDateTime.of(2026, 10, 16, 23, 30), false));
    }

    @Test
    void listsNewestFirstPagedAndWithoutDeletedOrOtherEvents() {
        assertEquals(List.of("m5", "m4"), ids(adapter.findByEventId("evt_1", 0, 2)));
        assertEquals(List.of("m3", "m2"), ids(adapter.findByEventId("evt_1", 1, 2)));
        assertEquals(List.of("m1"), ids(adapter.findByEventId("evt_1", 2, 2)));
        assertEquals(List.of(), ids(adapter.findByEventId("evt_1", 3, 2)));
    }

    @Test
    void findsTheMediaOfAMosaicItemByUrlWithinTheEvent() {
        List<String> urls = List.of(url("m2"), url("m_other"), url("m_deleted"), "https://cdn.vito.com/otra.jpg");

        assertEquals(List.of("m2"), ids(adapter.findByEventIdAndUrls("evt_1", urls)));
    }

    private static List<String> ids(List<Media> media) {
        return media.stream().map(Media::getId).toList();
    }

    private static String url(String id) {
        return "https://cdn.vito.com/events/" + id + ".jpg";
    }

    private static Media media(String id, String eventId, LocalDateTime uploadedAt, boolean deleted) {
        return Media.builder().id(id).eventId(eventId).uploadedBy("usr_1").uploadedAt(uploadedAt).deleted(deleted)
                .status(Media.MediaStatus.UPLOADED)
                .location(Media.S3Location.builder().key("events/" + id + ".jpg").url(url(id)).build())
                .build();
    }
}
