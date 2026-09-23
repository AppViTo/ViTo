package com.vito.archive.application.service;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.model.EventArchive;
import com.vito.archive.domain.port.inbound.ArchiveService;
import com.vito.archive.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceImplTest {
    
    @Mock
    private EventArchiveRepository archiveRepository;
    
    @Mock
    private ArchiveMediaRepository mediaRepository;
    
    @Mock
    private ArchiveCommentRepository commentRepository;
    
    @Mock
    private ShareService shareService;
    
    @Mock
    private ArchiveEventPublisher eventPublisher;
    
    private ArchiveServiceImpl archiveService;
    
    @BeforeEach
    void setUp() {
        archiveService = new ArchiveServiceImpl(
                archiveRepository,
                mediaRepository,
                commentRepository,
                shareService,
                eventPublisher
        );
    }
    
    @Test
    void createArchive_Success() {
        String eventId = "event-123";
        String organizerId = "user-123";
        String title = "Test Event Archive";
        
        ArchiveService.CreateArchiveCommand command = new ArchiveService.CreateArchiveCommand();
        command.setEventId(eventId);
        command.setOrganizerId(organizerId);
        command.setTitle(title);
        
        when(shareService.generateShareCode(anyString())).thenReturn("abc123");
        when(shareService.generateShareUrl(anyString(), anyString())).thenReturn("http://localhost/archive/share/abc123");
        
        EventArchive mockArchive = EventArchive.builder()
                .id("archive-123")
                .eventId(eventId)
                .organizerId(organizerId)
                .title(title)
                .status(EventArchive.ArchiveStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        when(archiveRepository.save(any(EventArchive.class))).thenReturn(mockArchive);
        
        EventArchive result = archiveService.createArchive(command);
        
        assertNotNull(result);
        assertEquals(eventId, result.getEventId());
        assertEquals(organizerId, result.getOrganizerId());
        assertEquals(title, result.getTitle());
        assertEquals(EventArchive.ArchiveStatus.PENDING, result.getStatus());
        
        verify(archiveRepository, times(1)).save(any(EventArchive.class));
        verify(eventPublisher, times(1)).publishArchiveCreated(any(EventArchive.class));
    }
    
    @Test
    void getArchive_Success() {
        String archiveId = "archive-123";
        
        EventArchive mockArchive = EventArchive.builder()
                .id(archiveId)
                .eventId("event-123")
                .organizerId("user-123")
                .title("Test Archive")
                .status(EventArchive.ArchiveStatus.COMPLETED)
                .build();
        
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.of(mockArchive));
        
        EventArchive result = archiveService.getArchive(archiveId);
        
        assertNotNull(result);
        assertEquals(archiveId, result.getId());
        
        verify(archiveRepository, times(1)).findById(archiveId);
    }
    
    @Test
    void getArchive_NotFound() {
        String archiveId = "archive-123";
        
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, 
                () -> archiveService.getArchive(archiveId));
    }
    
    @Test
    void updateArchive_Success() {
        String archiveId = "archive-123";
        
        EventArchive mockArchive = EventArchive.builder()
                .id(archiveId)
                .eventId("event-123")
                .organizerId("user-123")
                .title("Test Archive")
                .status(EventArchive.ArchiveStatus.COMPLETED)
                .sharing(EventArchive.SharingSettings.builder().build())
                .build();
        
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.of(mockArchive));
        when(archiveRepository.save(any(EventArchive.class))).thenReturn(mockArchive);
        
        ArchiveService.UpdateArchiveCommand command = new ArchiveService.UpdateArchiveCommand();
        command.setTitle("Updated Title");
        command.setIsPublic(true);
        
        EventArchive result = archiveService.updateArchive(archiveId, command);
        
        assertNotNull(result);
        assertEquals("Updated Title", result.getTitle());
        assertTrue(result.getSharing().isIsPublic());
        
        verify(archiveRepository, times(1)).save(any(EventArchive.class));
        verify(eventPublisher, times(1)).publishArchiveUpdated(any(EventArchive.class));
    }
    
    @Test
    void uploadMedia_Success() {
        String archiveId = "archive-123";
        
        EventArchive mockArchive = EventArchive.builder()
                .id(archiveId)
                .eventId("event-123")
                .statistics(EventArchive.EventStatistics.builder().build())
                .build();
        
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.of(mockArchive));
        
        ArchiveMedia mockMedia = ArchiveMedia.builder()
                .id("media-123")
                .archiveId(archiveId)
                .type(ArchiveMedia.MediaType.PHOTO)
                .url("http://example.com/photo.jpg")
                .uploadedAt(LocalDateTime.now())
                .build();
        
        when(mediaRepository.save(any(ArchiveMedia.class))).thenReturn(mockMedia);
        when(archiveRepository.save(any(EventArchive.class))).thenReturn(mockArchive);
        
        ArchiveService.UploadMediaCommand command = new ArchiveService.UploadMediaCommand();
        command.setUploaderId("user-123");
        command.setType(ArchiveMedia.MediaType.PHOTO);
        command.setUrl("http://example.com/photo.jpg");
        
        ArchiveMedia result = archiveService.uploadMedia(archiveId, command);
        
        assertNotNull(result);
        assertEquals(archiveId, result.getArchiveId());
        assertEquals(ArchiveMedia.MediaType.PHOTO, result.getType());
        
        verify(mediaRepository, times(1)).save(any(ArchiveMedia.class));
        verify(eventPublisher, times(1)).publishMediaUploaded(eq(archiveId), any(ArchiveMedia.class));
    }
    
    @Test
    void addComment_Success() {
        String archiveId = "archive-123";
        
        EventArchive mockArchive = EventArchive.builder()
                .id(archiveId)
                .statistics(EventArchive.EventStatistics.builder().build())
                .build();
        
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.of(mockArchive));
        
        ArchiveComment mockComment = ArchiveComment.builder()
                .id("comment-123")
                .archiveId(archiveId)
                .userId("user-123")
                .content("Great event!")
                .createdAt(LocalDateTime.now())
                .build();
        
        when(commentRepository.save(any(ArchiveComment.class))).thenReturn(mockComment);
        when(archiveRepository.save(any(EventArchive.class))).thenReturn(mockArchive);
        
        ArchiveService.AddCommentCommand command = new ArchiveService.AddCommentCommand();
        command.setUserId("user-123");
        command.setUsername("testuser");
        command.setDisplayName("Test User");
        command.setContent("Great event!");
        
        ArchiveComment result = archiveService.addComment(archiveId, command);
        
        assertNotNull(result);
        assertEquals(archiveId, result.getArchiveId());
        assertEquals("Great event!", result.getContent());
        
        verify(commentRepository, times(1)).save(any(ArchiveComment.class));
        verify(eventPublisher, times(1)).publishCommentAdded(eq(archiveId), any(ArchiveComment.class));
    }
    
    @Test
    void likeMedia_Success() {
        String mediaId = "media-123";
        String userId = "user-123";
        
        ArchiveMedia mockMedia = ArchiveMedia.builder()
                .id(mediaId)
                .archiveId("archive-123")
                .likesCount(0)
                .build();
        
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(mockMedia));
        when(mediaRepository.save(any(ArchiveMedia.class))).thenReturn(mockMedia);
        
        EventArchive mockArchive = EventArchive.builder()
                .id("archive-123")
                .statistics(EventArchive.EventStatistics.builder().build())
                .build();
        
        when(archiveRepository.findById("archive-123")).thenReturn(Optional.of(mockArchive));
        when(archiveRepository.save(any(EventArchive.class))).thenReturn(mockArchive);
        
        archiveService.likeMedia(mediaId, userId);
        
        verify(mediaRepository, times(1)).save(any(ArchiveMedia.class));
        verify(archiveRepository, times(1)).save(any(EventArchive.class));
        verify(eventPublisher, times(1)).publishMediaLiked(eq("archive-123"), eq(mediaId), eq(userId));
    }
    
    @Test
    void deleteComment_Success() {
        String commentId = "comment-123";
        String userId = "user-123";
        
        ArchiveComment mockComment = ArchiveComment.builder()
                .id(commentId)
                .archiveId("archive-123")
                .userId(userId)
                .build();
        
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));
        doNothing().when(commentRepository).deleteById(commentId);
        
        EventArchive mockArchive = EventArchive.builder()
                .id("archive-123")
                .statistics(EventArchive.EventStatistics.builder().totalComments(1).build())
                .build();
        
        when(archiveRepository.findById("archive-123")).thenReturn(Optional.of(mockArchive));
        when(archiveRepository.save(any(EventArchive.class))).thenReturn(mockArchive);
        
        archiveService.deleteComment(commentId, userId);
        
        verify(commentRepository, times(1)).deleteById(commentId);
        verify(archiveRepository, times(1)).save(any(EventArchive.class));
    }
    
    @Test
    void deleteComment_Unauthorized() {
        String commentId = "comment-123";
        String wrongUserId = "user-456";
        
        ArchiveComment mockComment = ArchiveComment.builder()
                .id(commentId)
                .archiveId("archive-123")
                .userId("user-123")
                .build();
        
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));
        
        assertThrows(RuntimeException.class, 
                () -> archiveService.deleteComment(commentId, wrongUserId));
    }
}
