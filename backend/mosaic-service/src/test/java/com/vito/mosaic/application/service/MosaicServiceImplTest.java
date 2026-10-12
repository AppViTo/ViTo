package com.vito.mosaic.application.service;

import com.vito.mosaic.domain.exception.MosaicException;
import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.port.inbound.MosaicService.*;
import com.vito.mosaic.domain.port.outbound.MosaicEventPublisher;
import com.vito.mosaic.domain.port.outbound.MosaicFeedRepository;
import com.vito.mosaic.domain.port.outbound.MosaicGridRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MosaicServiceImplTest {

    @Mock
    private MosaicFeedRepository feedRepository;

    @Mock
    private MosaicGridRepository gridRepository;

    @Mock
    private MosaicEventPublisher eventPublisher;

    @InjectMocks
    private MosaicServiceImpl mosaicService;

    private MosaicFeedItem testFeedItem;

    @BeforeEach
    void setUp() {
        testFeedItem = MosaicFeedItem.builder()
                .id("item123")
                .eventId("event123")
                .userId("user123")
                .username("testuser")
                .displayName("Test User")
                .avatar("https://example.com/avatar.jpg")
                .type(MosaicFeedItem.FeedItemType.POST)
                .content("Test content")
                .mediaUrls(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .likesCount(0)
                .commentsCount(0)
                .likedBy(new ArrayList<>())
                .isLiked(false)
                .build();
    }

    @Test
    void addPost_Success() {
        // Arrange
        AddPostCommand command = AddPostCommand.builder()
                .eventId("event123")
                .userId("user123")
                .username("testuser")
                .displayName("Test User")
                .content("Test post content")
                .build();

        when(feedRepository.save(any(MosaicFeedItem.class))).thenReturn(testFeedItem);
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.empty());

        // Act
        MosaicFeedItem result = mosaicService.addPost(command);

        // Assert
        assertNotNull(result);
        assertEquals("item123", result.getId());

        verify(feedRepository).save(any(MosaicFeedItem.class));
        verify(eventPublisher).publishNewItem("event123", testFeedItem);
    }

    @Test
    void addPhoto_Success() {
        // Arrange
        AddPhotoCommand command = AddPhotoCommand.builder()
                .eventId("event123")
                .userId("user123")
                .username("testuser")
                .displayName("Test User")
                .content("Photo caption")
                .mediaUrls(java.util.List.of("https://example.com/photo.jpg"))
                .build();

        when(feedRepository.save(any(MosaicFeedItem.class))).thenReturn(testFeedItem);
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.empty());

        // Act
        MosaicFeedItem result = mosaicService.addPhoto(command);

        // Assert
        assertNotNull(result);

        verify(feedRepository).save(any(MosaicFeedItem.class));
        verify(eventPublisher).publishNewItem("event123", testFeedItem);
    }

    @Test
    void addCheckIn_Success() {
        // Arrange
        AddCheckInCommand command = AddCheckInCommand.builder()
                .eventId("event123")
                .userId("user123")
                .username("testuser")
                .displayName("Test User")
                .location("Main Entrance")
                .build();

        when(feedRepository.save(any(MosaicFeedItem.class))).thenReturn(testFeedItem);
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.empty());

        // Act
        MosaicFeedItem result = mosaicService.addCheckIn(command);

        // Assert
        assertNotNull(result);

        verify(feedRepository).save(any(MosaicFeedItem.class));
        verify(eventPublisher).publishNewItem("event123", testFeedItem);
    }

    @Test
    void getEventFeed_Success() {
        // Arrange
        when(feedRepository.findByEventId("event123", 0, 20))
                .thenReturn(java.util.List.of(testFeedItem));

        // Act
        var result = mosaicService.getEventFeed("event123", 0, 20);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        verify(feedRepository).findByEventId("event123", 0, 20);
    }

    @Test
    void getEventGrid_Success() {
        // Arrange
        MosaicGrid grid = MosaicGrid.createEmpty("event123", 4, 4);
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.of(grid));

        // Act
        MosaicGrid result = mosaicService.getEventGrid("event123");

        // Assert
        assertNotNull(result);
        assertEquals("event123", result.getEventId());

        verify(gridRepository).findByEventId("event123");
    }

    @Test
    void getEventGrid_NotInitialized_ThrowsException() {
        // Arrange
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.empty());

        // Act & Assert
        MosaicException exception = assertThrows(
                MosaicException.class,
                () -> mosaicService.getEventGrid("event123")
        );
        assertEquals("Mosaic grid is not initialized", exception.getMessage());
    }

    @Test
    void initializeGrid_Success() {
        // Arrange
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.empty());
        when(gridRepository.save(any(MosaicGrid.class))).thenReturn(MosaicGrid.createEmpty("event123", 4, 4));

        // Act
        MosaicGrid result = mosaicService.initializeGrid("event123", 4, 4);

        // Assert
        assertNotNull(result);
        assertEquals("event123", result.getEventId());

        verify(gridRepository).findByEventId("event123");
        verify(gridRepository).save(any(MosaicGrid.class));
    }

    @Test
    void initializeGrid_AlreadyExists_ReturnsExisting() {
        // Arrange
        MosaicGrid existingGrid = MosaicGrid.createEmpty("event123", 4, 4);
        when(gridRepository.findByEventId("event123")).thenReturn(Optional.of(existingGrid));

        // Act
        MosaicGrid result = mosaicService.initializeGrid("event123", 4, 4);

        // Assert
        assertNotNull(result);
        assertEquals("event123", result.getEventId());

        verify(gridRepository).findByEventId("event123");
        verify(gridRepository, never()).save(any());
    }

    @Test
    void likePost_Success() {
        // Arrange
        when(feedRepository.findById("item123")).thenReturn(Optional.of(testFeedItem));
        when(feedRepository.save(any(MosaicFeedItem.class))).thenReturn(testFeedItem);

        // Act
        mosaicService.likePost("item123", "user456");

        // Assert
        verify(feedRepository).findById("item123");
        verify(feedRepository).save(any(MosaicFeedItem.class));
        verify(eventPublisher).publishLikeUpdate(eq("event123"), eq("item123"), anyInt());
    }

    @Test
    void likePost_ItemNotFound_ThrowsException() {
        // Arrange
        when(feedRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        MosaicException exception = assertThrows(
                MosaicException.class,
                () -> mosaicService.likePost("nonexistent", "user456")
        );
        assertEquals("Mosaic item not found", exception.getMessage());
    }

    @Test
    void unlikePost_Success() {
        // Arrange
        testFeedItem.like("user456");
        when(feedRepository.findById("item123")).thenReturn(Optional.of(testFeedItem));
        when(feedRepository.save(any(MosaicFeedItem.class))).thenReturn(testFeedItem);

        // Act
        mosaicService.unlikePost("item123", "user456");

        // Assert
        verify(feedRepository).findById("item123");
        verify(feedRepository).save(any(MosaicFeedItem.class));
        verify(eventPublisher).publishLikeUpdate(eq("event123"), eq("item123"), anyInt());
    }
}
