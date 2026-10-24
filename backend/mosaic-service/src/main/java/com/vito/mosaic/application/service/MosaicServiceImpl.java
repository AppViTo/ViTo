package com.vito.mosaic.application.service;

import com.vito.mosaic.domain.exception.MosaicException;
import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.model.MosaicItem;
import com.vito.mosaic.domain.port.inbound.MosaicService;
import com.vito.mosaic.domain.port.outbound.EventOrganizerLookup;
import com.vito.mosaic.domain.port.outbound.MosaicEventPublisher;
import com.vito.mosaic.domain.port.outbound.MosaicFeedRepository;
import com.vito.mosaic.domain.port.outbound.MosaicGridRepository;
import com.vito.mosaic.domain.port.outbound.MosaicRealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MosaicServiceImpl implements MosaicService {
    
    private final MosaicFeedRepository feedRepository;
    private final MosaicGridRepository gridRepository;
    private final MosaicEventPublisher eventPublisher;
    private final EventOrganizerLookup eventOrganizerLookup;
    private final MosaicRealtimeNotifier realtimeNotifier;
    
    @Override
    public MosaicFeedItem addPost(AddPostCommand command) {
        log.info("Adding post to event: {} by user: {}", command.getEventId(), command.getUserId());
        
        MosaicFeedItem item = MosaicFeedItem.builder()
                .eventId(command.getEventId())
                .userId(command.getUserId())
                .username(command.getUsername())
                .displayName(command.getDisplayName())
                .avatar(command.getAvatar())
                .type(MosaicFeedItem.FeedItemType.POST)
                .content(command.getContent())
                .mediaUrls(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .likesCount(0)
                .commentsCount(0)
                .likedBy(new ArrayList<>())
                .isLiked(false)
                .build();
        
        MosaicFeedItem savedItem = feedRepository.save(item);
        
        // Update grid
        updateGrid(command.getEventId(), savedItem);
        
        // Publish real-time event
        eventPublisher.publishNewItem(command.getEventId(), savedItem);
        
        log.info("Post added to event: {} item: {}", command.getEventId(), savedItem.getId());
        
        return savedItem;
    }
    
    @Override
    public MosaicFeedItem addPhoto(AddPhotoCommand command) {
        log.info("Adding photo to event: {} by user: {}", command.getEventId(), command.getUserId());
        
        MosaicFeedItem item = MosaicFeedItem.builder()
                .eventId(command.getEventId())
                .userId(command.getUserId())
                .username(command.getUsername())
                .displayName(command.getDisplayName())
                .avatar(command.getAvatar())
                .type(MosaicFeedItem.FeedItemType.PHOTO)
                .content(command.getContent())
                .mediaUrls(command.getMediaUrls())
                .timestamp(LocalDateTime.now())
                .likesCount(0)
                .commentsCount(0)
                .likedBy(new ArrayList<>())
                .isLiked(false)
                .build();
        
        MosaicFeedItem savedItem = feedRepository.save(item);
        
        // Update grid
        updateGrid(command.getEventId(), savedItem);
        
        // Publish real-time event
        eventPublisher.publishNewItem(command.getEventId(), savedItem);
        
        log.info("Photo added to event: {} item: {}", command.getEventId(), savedItem.getId());
        
        return savedItem;
    }
    
    @Override
    public MosaicFeedItem addCheckIn(AddCheckInCommand command) {
        log.info("Adding check-in to event: {} by user: {}", command.getEventId(), command.getUserId());
        
        MosaicFeedItem item = MosaicFeedItem.builder()
                .eventId(command.getEventId())
                .userId(command.getUserId())
                .username(command.getUsername())
                .displayName(command.getDisplayName())
                .avatar(command.getAvatar())
                .type(MosaicFeedItem.FeedItemType.CHECK_IN)
                .content(command.getLocation() != null ? 
                        "Checked in at " + command.getLocation() : "Checked in")
                .mediaUrls(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .likesCount(0)
                .commentsCount(0)
                .likedBy(new ArrayList<>())
                .isLiked(false)
                .build();
        
        MosaicFeedItem savedItem = feedRepository.save(item);
        
        // Update grid
        updateGrid(command.getEventId(), savedItem);
        
        // Publish real-time event
        eventPublisher.publishNewItem(command.getEventId(), savedItem);
        
        log.info("Check-in added to event: {} item: {}", command.getEventId(), savedItem.getId());
        
        return savedItem;
    }
    
    @Override
    public List<MosaicFeedItem> getEventFeed(String eventId, int page, int size) {
        return feedRepository.findByEventId(eventId, page, size);
    }
    
    @Override
    public MosaicGrid getEventGrid(String eventId) {
        return gridRepository.findByEventId(eventId)
                .orElseThrow(MosaicException::gridNotInitialized);
    }
    
    @Override
    public MosaicGrid initializeGrid(String eventId, int rows, int cols) {
        log.info("Initializing grid for event: {} rows: {} cols: {}", eventId, rows, cols);
        
        MosaicGrid existingGrid = gridRepository.findByEventId(eventId).orElse(null);
        if (existingGrid != null) {
            return existingGrid;
        }
        
        MosaicGrid grid = MosaicGrid.createEmpty(eventId, rows, cols);
        MosaicGrid savedGrid = gridRepository.save(grid);
        
        log.info("Grid initialized for event: {}", eventId);
        
        return savedGrid;
    }
    
    @Override
    public void likePost(String itemId, String userId) {
        log.info("Liking item: {} by user: {}", itemId, userId);
        
        MosaicFeedItem item = findActiveItem(itemId);
        
        item.like(userId);
        feedRepository.save(item);
        
        eventPublisher.publishLikeUpdate(item.getEventId(), itemId, item.getLikesCount());
    }
    
    @Override
    public void unlikePost(String itemId, String userId) {
        log.info("Unliking item: {} by user: {}", itemId, userId);
        
        MosaicFeedItem item = findActiveItem(itemId);
        
        item.unlike(userId);
        feedRepository.save(item);
        
        eventPublisher.publishLikeUpdate(item.getEventId(), itemId, item.getLikesCount());
    }
    
    @Override
    public MosaicFeedItem getFeedItem(String itemId) {
        return findActiveItem(itemId);
    }

    @Override
    public void removeItem(String itemId, String userId) {
        MosaicFeedItem item = findActiveItem(itemId);
        String eventId = item.getEventId();

        String organizerId = eventOrganizerLookup.findOrganizerId(eventId).orElse(null);
        if (organizerId == null || !organizerId.equals(userId)) {
            log.info("Removal of item {} denied: user {} is not the organizer of event {}", itemId, userId, eventId);
            throw MosaicException.notOrganizer();
        }

        item.markRemoved(userId, LocalDateTime.now());
        feedRepository.save(item);

        gridRepository.findByEventId(eventId).ifPresent(grid -> {
            if (grid.removeItem(itemId)) {
                gridRepository.save(grid);
            }
        });

        // Primero el WS: un Kafka caído no debe retrasar el aviso a los clientes conectados
        realtimeNotifier.itemRemoved(eventId, itemId);
        try {
            eventPublisher.publishItemRemoved(eventId, itemId);
        } catch (Exception e) {
            log.error("Failed to publish removal of item {} for event {}", itemId, eventId, e);
        }

        log.info("Item {} removed from event {} by organizer {}", itemId, eventId, userId);
    }

    /** Un ítem retirado (T-B26) responde como inexistente: 404 en getMosaicItem y en los likes. */
    private MosaicFeedItem findActiveItem(String itemId) {
        return feedRepository.findById(itemId)
                .filter(item -> !item.isRemoved())
                .orElseThrow(MosaicException::itemNotFound);
    }
    
    private void updateGrid(String eventId, MosaicFeedItem feedItem) {
        try {
            MosaicGrid grid = gridRepository.findByEventId(eventId).orElse(null);
            if (grid == null) {
                grid = MosaicGrid.createEmpty(eventId, 4, 4);
            }
            
            int nextIndex = (grid.getLastUpdatedIndex() + 1) % 16;
            
            MosaicItem mosaicItem = MosaicItem.builder()
                    .id(feedItem.getId())
                    .eventId(feedItem.getEventId())
                    .userId(feedItem.getUserId())
                    .username(feedItem.getUsername())
                    .displayName(feedItem.getDisplayName())
                    .avatar(feedItem.getAvatar())
                    .type(MosaicItem.MosaicItemType.valueOf(feedItem.getType().name()))
                    .content(feedItem.getContent())
                    .mediaUrl(feedItem.getMediaUrls().isEmpty() ? null : feedItem.getMediaUrls().get(0))
                    .timestamp(feedItem.getTimestamp())
                    .position(MosaicItem.MosaicPosition.defaultPosition(nextIndex))
                    .likesCount(feedItem.getLikesCount())
                    .commentsCount(feedItem.getCommentsCount())
                    .build();
            
            grid.addItem(mosaicItem, nextIndex);
            gridRepository.save(grid);
            
            eventPublisher.publishGridUpdate(eventId, mosaicItem, nextIndex);
        } catch (Exception e) {
            log.error("Failed to update grid for event: {}", eventId, e);
        }
    }
}
