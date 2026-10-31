package com.vito.mosaic.application.service;

import com.vito.mosaic.domain.exception.MosaicException;
import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.model.MosaicItem;
import com.vito.mosaic.domain.port.outbound.EventOrganizerLookup;
import com.vito.mosaic.domain.port.outbound.MosaicEventPublisher;
import com.vito.mosaic.domain.port.outbound.MosaicFeedRepository;
import com.vito.mosaic.domain.port.outbound.MosaicGridRepository;
import com.vito.mosaic.domain.port.outbound.MosaicRealtimeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** T-B26 (excepción A3): el organizador retira un ítem del mosaico. */
@ExtendWith(MockitoExtension.class)
class MosaicServiceRemoveItemTest {

    private static final String EVENT = "evt_1";
    private static final String ITEM = "itm_1";
    private static final String ORGANIZER = "usr_org";

    @Mock MosaicFeedRepository feedRepository;
    @Mock MosaicGridRepository gridRepository;
    @Mock MosaicEventPublisher eventPublisher;
    @Mock EventOrganizerLookup eventOrganizerLookup;
    @Mock MosaicRealtimeNotifier realtimeNotifier;

    @InjectMocks MosaicServiceImpl service;

    private MosaicFeedItem item;

    @BeforeEach
    void setUp() {
        item = MosaicFeedItem.builder()
                .id(ITEM)
                .eventId(EVENT)
                .userId("usr_guest")
                .type(MosaicFeedItem.FeedItemType.PHOTO)
                .mediaUrls(new ArrayList<>())
                .likedBy(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static MosaicGrid gridWithItem() {
        MosaicGrid grid = MosaicGrid.createEmpty(EVENT, 4, 4);
        grid.addItem(MosaicItem.builder().id(ITEM).eventId(EVENT).build(), 0);
        grid.addItem(MosaicItem.builder().id("itm_2").eventId(EVENT).build(), 1);
        return grid;
    }

    @Test
    void organizerRemovesItemFromFeedAndGridAndNotifies() {
        MosaicGrid grid = gridWithItem();
        when(feedRepository.findById(ITEM)).thenReturn(Optional.of(item));
        when(eventOrganizerLookup.findOrganizerId(EVENT)).thenReturn(Optional.of(ORGANIZER));
        when(gridRepository.findByEventId(EVENT)).thenReturn(Optional.of(grid));

        service.removeItem(ITEM, ORGANIZER);

        ArgumentCaptor<MosaicFeedItem> saved = ArgumentCaptor.forClass(MosaicFeedItem.class);
        verify(feedRepository).save(saved.capture());
        assertTrue(saved.getValue().isRemoved());
        assertEquals(ORGANIZER, saved.getValue().getRemovedBy());

        verify(gridRepository).save(grid);
        assertNull(grid.getGrid().get(0).get(0));
        assertEquals("itm_2", grid.getGrid().get(0).get(1).getId());
        assertEquals(1, grid.getTotalItems());

        verify(realtimeNotifier).itemRemoved(EVENT, ITEM);
        verify(eventPublisher).publishItemRemoved(EVENT, ITEM);
    }

    @Test
    void nonOrganizerGets403AndNothingChanges() {
        when(feedRepository.findById(ITEM)).thenReturn(Optional.of(item));
        when(eventOrganizerLookup.findOrganizerId(EVENT)).thenReturn(Optional.of(ORGANIZER));

        MosaicException ex = assertThrows(MosaicException.class, () -> service.removeItem(ITEM, "usr_guest"));

        assertEquals(MosaicException.Reason.FORBIDDEN, ex.getReason());
        assertEquals("Solo el organizador puede retirar fotos del mosaico", ex.getMessage());
        verify(feedRepository, never()).save(any());
        verifyNoInteractions(gridRepository, realtimeNotifier, eventPublisher);
    }

    @Test
    void unknownOrInvisibleEventCountsAsNotOrganizer() {
        when(feedRepository.findById(ITEM)).thenReturn(Optional.of(item));
        when(eventOrganizerLookup.findOrganizerId(EVENT)).thenReturn(Optional.empty());

        MosaicException ex = assertThrows(MosaicException.class, () -> service.removeItem(ITEM, ORGANIZER));

        assertEquals(MosaicException.Reason.FORBIDDEN, ex.getReason());
        verify(feedRepository, never()).save(any());
    }

    @Test
    void missingItemIs404() {
        when(feedRepository.findById(ITEM)).thenReturn(Optional.empty());

        MosaicException ex = assertThrows(MosaicException.class, () -> service.removeItem(ITEM, ORGANIZER));

        assertEquals(MosaicException.Reason.NOT_FOUND, ex.getReason());
        verifyNoInteractions(eventOrganizerLookup);
    }

    @Test
    void removedItemIs404ForRemovalItemAndLikes() {
        item.markRemoved(ORGANIZER, LocalDateTime.now());
        when(feedRepository.findById(ITEM)).thenReturn(Optional.of(item));

        assertEquals(MosaicException.Reason.NOT_FOUND,
                assertThrows(MosaicException.class, () -> service.removeItem(ITEM, ORGANIZER)).getReason());
        assertEquals(MosaicException.Reason.NOT_FOUND,
                assertThrows(MosaicException.class, () -> service.getFeedItem(ITEM)).getReason());
        assertEquals(MosaicException.Reason.NOT_FOUND,
                assertThrows(MosaicException.class, () -> service.likePost(ITEM, "usr_guest")).getReason());
        assertEquals(MosaicException.Reason.NOT_FOUND,
                assertThrows(MosaicException.class, () -> service.unlikePost(ITEM, "usr_guest")).getReason());
        verify(feedRepository, never()).save(any());
    }

    @Test
    void kafkaFailureDoesNotUndoTheRemoval() {
        when(feedRepository.findById(ITEM)).thenReturn(Optional.of(item));
        when(eventOrganizerLookup.findOrganizerId(EVENT)).thenReturn(Optional.of(ORGANIZER));
        when(gridRepository.findByEventId(EVENT)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("broker caido")).when(eventPublisher)
                .publishItemRemoved(anyString(), anyString());

        service.removeItem(ITEM, ORGANIZER);

        verify(feedRepository).save(item);
        verify(realtimeNotifier).itemRemoved(EVENT, ITEM);
    }
}
