package com.vito.mosaic.infrastructure.adapter.inbound.rest;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.model.MosaicItem;
import com.vito.mosaic.domain.port.outbound.EventOrganizerLookup;
import com.vito.mosaic.domain.port.outbound.MosaicEventPublisher;
import com.vito.mosaic.infrastructure.websocket.MosaicWebSocketHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-B26 (excepción A3) de punta a punta sobre Mongo: {@code DELETE /api/v1/mosaic/item/{itemId}}
 * solo para el organizador; el ítem retirado deja de salir en feed, grilla, getMosaicItem y likes,
 * y los clientes del WebSocket reciben ITEM_REMOVED.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MosaicModerationIntegrationTest {

    private static final String EVENT = "evt_tb26";
    private static final String ORGANIZER = "usr_tb26_org";
    private static final String GUEST = "usr_tb26_guest";
    private static final String REMOVED = "itm_tb26_a";
    private static final String KEPT = "itm_tb26_b";

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    @MockitoBean EventOrganizerLookup eventOrganizerLookup;
    @MockitoBean MosaicEventPublisher eventPublisher;
    @MockitoSpyBean MosaicWebSocketHandler webSocketHandler;

    @Value("${jwt.secret}")
    String jwtSecret;

    @BeforeEach
    void seedMosaic() {
        Query byEvent = new Query(Criteria.where("eventId").is(EVENT));
        mongo.remove(byEvent, "mosaic_feed");
        mongo.remove(byEvent, "mosaic_grids");

        LocalDateTime now = LocalDateTime.now();
        mongo.save(photo(REMOVED, now.minusMinutes(1)), "mosaic_feed");
        mongo.save(photo(KEPT, now), "mosaic_feed");

        MosaicGrid grid = MosaicGrid.createEmpty(EVENT, 4, 4);
        grid.addItem(MosaicItem.builder().id(REMOVED).eventId(EVENT).userId(GUEST).build(), 0);
        grid.addItem(MosaicItem.builder().id(KEPT).eventId(EVENT).userId(GUEST).build(), 1);
        mongo.save(grid, "mosaic_grids");

        when(eventOrganizerLookup.findOrganizerId(EVENT)).thenReturn(Optional.of(ORGANIZER));
    }

    private static MosaicFeedItem photo(String id, LocalDateTime at) {
        return MosaicFeedItem.builder()
                .id(id)
                .eventId(EVENT)
                .userId(GUEST)
                .username("invitado")
                .type(MosaicFeedItem.FeedItemType.PHOTO)
                .mediaUrls(new ArrayList<>())
                .likedBy(new ArrayList<>())
                .timestamp(at)
                .build();
    }

    private String bearer(String userId) {
        return "Bearer " + Jwts.builder()
                .subject(userId)
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }

    @Test
    void organizerRemovesPhotoFromFeedGridItemAndLikes() throws Exception {
        mvc.perform(delete("/api/v1/mosaic/item/" + REMOVED).header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(get("/api/v1/mosaic/feed/" + EVENT).header(HttpHeaders.AUTHORIZATION, bearer(GUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(KEPT)))
                .andExpect(jsonPath("$[0].removed").doesNotExist())
                .andExpect(jsonPath("$[0].removedBy").doesNotExist());

        mvc.perform(get("/api/v1/mosaic/grid/" + EVENT).header(HttpHeaders.AUTHORIZATION, bearer(GUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grid[0][1].id").value(KEPT))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(content().string(not(containsString(REMOVED))));

        mvc.perform(get("/api/v1/mosaic/item/" + REMOVED).header(HttpHeaders.AUTHORIZATION, bearer(GUEST)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/mosaic/item/" + REMOVED + "/like").header(HttpHeaders.AUTHORIZATION, bearer(GUEST)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/mosaic/item/" + REMOVED).header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        verify(webSocketHandler).itemRemoved(EVENT, REMOVED);
        verify(eventPublisher).publishItemRemoved(EVENT, REMOVED);
    }

    @Test
    void guestGets403EnvelopeAndPhotoStays() throws Exception {
        mvc.perform(delete("/api/v1/mosaic/item/" + REMOVED).header(HttpHeaders.AUTHORIZATION, bearer(GUEST)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Solo el organizador puede retirar fotos del mosaico"))
                .andExpect(jsonPath("$.timestamp").exists());

        mvc.perform(get("/api/v1/mosaic/feed/" + EVENT).header(HttpHeaders.AUTHORIZATION, bearer(GUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(KEPT, REMOVED)));

        verify(webSocketHandler, never()).itemRemoved(anyString(), anyString());
        verify(eventPublisher, never()).publishItemRemoved(anyString(), anyString());
    }

    @Test
    void unknownItemIs404() throws Exception {
        mvc.perform(delete("/api/v1/mosaic/item/itm_no_existe").header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
