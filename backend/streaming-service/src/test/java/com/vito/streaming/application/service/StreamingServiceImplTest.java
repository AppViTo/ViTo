package com.vito.streaming.application.service;

import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.LiveStream;
import com.vito.streaming.domain.model.Reaction;
import com.vito.streaming.domain.port.inbound.StreamingService;
import com.vito.streaming.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamingServiceImplTest {
    
    @Mock
    private LiveStreamRepository liveStreamRepository;
    
    @Mock
    private ReactionRepository reactionRepository;
    
    @Mock
    private ChatRepository chatRepository;
    
    @Mock
    private StreamingEventPublisher eventPublisher;
    
    @Mock
    private MediaServerPort mediaServerPort;
    
    private StreamingServiceImpl streamingService;
    
    @BeforeEach
    void setUp() {
        streamingService = new StreamingServiceImpl(
                liveStreamRepository,
                reactionRepository,
                chatRepository,
                eventPublisher,
                mediaServerPort
        );
    }
    
    @Test
    void createStream_Success() {
        String eventId = "event-123";
        String organizerId = "user-123";
        String title = "Test Stream";
        
        StreamingService.CreateStreamCommand command = new StreamingService.CreateStreamCommand();
        command.setEventId(eventId);
        command.setOrganizerId(organizerId);
        command.setTitle(title);
        
        LiveStream mockStream = LiveStream.builder()
                .id("stream-123")
                .eventId(eventId)
                .organizerId(organizerId)
                .title(title)
                .status(LiveStream.StreamStatus.IDLE)
                .createdAt(LocalDateTime.now())
                .build();
        
        when(liveStreamRepository.save(any(LiveStream.class))).thenReturn(mockStream);
        
        LiveStream result = streamingService.createStream(command);
        
        assertNotNull(result);
        assertEquals(eventId, result.getEventId());
        assertEquals(organizerId, result.getOrganizerId());
        assertEquals(title, result.getTitle());
        assertEquals(LiveStream.StreamStatus.IDLE, result.getStatus());
        
        verify(liveStreamRepository, times(1)).save(any(LiveStream.class));
    }
    
    @Test
    void startStream_Success() {
        String streamId = "stream-123";
        String organizerId = "user-123";
        
        LiveStream mockStream = LiveStream.builder()
                .id(streamId)
                .eventId("event-123")
                .organizerId(organizerId)
                .title("Test Stream")
                .status(LiveStream.StreamStatus.IDLE)
                .streamKey("key-123")
                .build();
        
        when(liveStreamRepository.findById(streamId)).thenReturn(mockStream);
        when(mediaServerPort.startStream(anyString())).thenReturn(true);
        when(liveStreamRepository.save(any(LiveStream.class))).thenReturn(mockStream);
        
        LiveStream result = streamingService.startStream(streamId, organizerId);
        
        assertNotNull(result);
        assertEquals(LiveStream.StreamStatus.LIVE, result.getStatus());
        assertNotNull(result.getStartedAt());
        
        verify(mediaServerPort, times(1)).startStream(anyString());
        verify(liveStreamRepository, times(1)).save(any(LiveStream.class));
        verify(eventPublisher, times(1)).publishStreamStarted(streamId, organizerId);
    }
    
    @Test
    void startStream_Unauthorized() {
        String streamId = "stream-123";
        String wrongUserId = "user-456";
        
        LiveStream mockStream = LiveStream.builder()
                .id(streamId)
                .eventId("event-123")
                .organizerId("user-123")
                .title("Test Stream")
                .status(LiveStream.StreamStatus.IDLE)
                .build();
        
        when(liveStreamRepository.findById(streamId)).thenReturn(mockStream);
        
        assertThrows(RuntimeException.class, 
                () -> streamingService.startStream(streamId, wrongUserId));
    }
    
    @Test
    void stopStream_Success() {
        String streamId = "stream-123";
        String organizerId = "user-123";
        
        LiveStream mockStream = LiveStream.builder()
                .id(streamId)
                .eventId("event-123")
                .organizerId(organizerId)
                .title("Test Stream")
                .status(LiveStream.StreamStatus.LIVE)
                .streamKey("key-123")
                .build();
        
        when(liveStreamRepository.findById(streamId)).thenReturn(mockStream);
        when(mediaServerPort.stopStream(anyString())).thenReturn(true);
        when(liveStreamRepository.save(any(LiveStream.class))).thenReturn(mockStream);
        
        LiveStream result = streamingService.stopStream(streamId, organizerId);
        
        assertNotNull(result);
        assertEquals(LiveStream.StreamStatus.ENDED, result.getStatus());
        assertNotNull(result.getEndedAt());
        
        verify(mediaServerPort, times(1)).stopStream(anyString());
        verify(liveStreamRepository, times(1)).save(any(LiveStream.class));
        verify(eventPublisher, times(1)).publishStreamEnded(streamId);
    }
    
    @Test
    void addReaction_Success() {
        String streamId = "stream-123";
        String userId = "user-123";
        
        StreamingService.AddReactionCommand command = new StreamingService.AddReactionCommand();
        command.setStreamId(streamId);
        command.setUserId(userId);
        command.setType(Reaction.ReactionType.LIKE);
        command.setEmoji("👍");
        
        Reaction mockReaction = Reaction.builder()
                .id("reaction-123")
                .streamId(streamId)
                .userId(userId)
                .type(Reaction.ReactionType.LIKE)
                .emoji("👍")
                .timestamp(LocalDateTime.now())
                .build();
        
        when(reactionRepository.save(any(Reaction.class))).thenReturn(mockReaction);
        when(reactionRepository.countByStreamId(streamId)).thenReturn(10L);
        
        Reaction result = streamingService.addReaction(command);
        
        assertNotNull(result);
        assertEquals(streamId, result.getStreamId());
        assertEquals(userId, result.getUserId());
        assertEquals(Reaction.ReactionType.LIKE, result.getType());
        
        verify(reactionRepository, times(1)).save(any(Reaction.class));
        verify(eventPublisher, times(1)).publishReaction(eq(streamId), any(Reaction.class));
    }
    
    @Test
    void sendChatMessage_Success() {
        String streamId = "stream-123";
        String userId = "user-123";
        String messageText = "Hello World";
        
        StreamingService.SendChatMessageCommand command = new StreamingService.SendChatMessageCommand();
        command.setStreamId(streamId);
        command.setUserId(userId);
        command.setMessage(messageText);
        
        ChatMessage mockMessage = ChatMessage.builder()
                .id("msg-123")
                .streamId(streamId)
                .userId(userId)
                .message(messageText)
                .type(ChatMessage.MessageType.NORMAL)
                .timestamp(LocalDateTime.now())
                .build();
        
        when(chatRepository.save(any(ChatMessage.class))).thenReturn(mockMessage);
        
        ChatMessage result = streamingService.sendChatMessage(command);
        
        assertNotNull(result);
        assertEquals(streamId, result.getStreamId());
        assertEquals(userId, result.getUserId());
        assertEquals(messageText, result.getMessage());
        
        verify(chatRepository, times(1)).save(any(ChatMessage.class));
        verify(eventPublisher, times(1)).publishChatMessage(eq(streamId), any(ChatMessage.class));
    }
    
    @Test
    void getStream_Success() {
        String streamId = "stream-123";
        
        LiveStream mockStream = LiveStream.builder()
                .id(streamId)
                .eventId("event-123")
                .organizerId("user-123")
                .title("Test Stream")
                .status(LiveStream.StreamStatus.LIVE)
                .build();
        
        when(liveStreamRepository.findById(streamId)).thenReturn(mockStream);
        
        LiveStream result = streamingService.getStream(streamId);
        
        assertNotNull(result);
        assertEquals(streamId, result.getId());
        
        verify(liveStreamRepository, times(1)).findById(streamId);
    }
    
    @Test
    void getReactions_Success() {
        String streamId = "stream-123";
        int page = 0;
        int size = 50;
        
        List<Reaction> mockReactions = List.of(
                Reaction.builder().id("r1").streamId(streamId).build(),
                Reaction.builder().id("r2").streamId(streamId).build()
        );
        
        when(reactionRepository.findByStreamId(streamId, page, size)).thenReturn(mockReactions);
        
        List<Reaction> result = streamingService.getReactions(streamId, page, size);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        
        verify(reactionRepository, times(1)).findByStreamId(streamId, page, size);
    }
    
    @Test
    void getChatMessages_Success() {
        String streamId = "stream-123";
        int page = 0;
        int size = 50;
        
        List<ChatMessage> mockMessages = List.of(
                ChatMessage.builder().id("m1").streamId(streamId).build(),
                ChatMessage.builder().id("m2").streamId(streamId).build()
        );
        
        when(chatRepository.findByStreamId(streamId, page, size)).thenReturn(mockMessages);
        
        List<ChatMessage> result = streamingService.getChatMessages(streamId, page, size);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        
        verify(chatRepository, times(1)).findByStreamId(streamId, page, size);
    }
    
    @Test
    void getStreamInfo_Success() {
        String streamId = "stream-123";
        
        LiveStream mockStream = LiveStream.builder()
                .id(streamId)
                .eventId("event-123")
                .organizerId("user-123")
                .title("Test Stream")
                .status(LiveStream.StreamStatus.LIVE)
                .build();
        
        when(liveStreamRepository.findById(streamId)).thenReturn(mockStream);
        when(reactionRepository.countByStreamId(streamId)).thenReturn(10L);
        when(chatRepository.countByStreamId(streamId)).thenReturn(5L);
        
        StreamingService.StreamInfo result = streamingService.getStreamInfo(streamId);
        
        assertNotNull(result);
        assertNotNull(result.getStream());
        assertEquals(streamId, result.getStream().getId());
        
        verify(liveStreamRepository, times(1)).findById(streamId);
        verify(reactionRepository, times(1)).countByStreamId(streamId);
        verify(chatRepository, times(1)).countByStreamId(streamId);
    }
}
