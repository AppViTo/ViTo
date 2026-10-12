package com.vito.chat.application.service;

import com.vito.chat.domain.exception.ChatException;
import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import com.vito.chat.domain.port.inbound.ChatService;
import com.vito.chat.domain.port.outbound.CachePort;
import com.vito.chat.domain.port.outbound.ChatEventPublisher;
import com.vito.chat.domain.port.outbound.ChatMessageRepository;
import com.vito.chat.domain.port.outbound.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository messageRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatEventPublisher eventPublisher;
    private final CachePort cachePort;

    @Override
    @Transactional
    public ChatRoom createRoom(ChatRoom room) {
        room.setCreatedAt(LocalDateTime.now());
        room.setLastMessageAt(LocalDateTime.now());
        room.setUnreadCount(0);
        
        ChatRoom saved = roomRepository.save(room);
        log.info("Created chat room: {} for event: {}", saved.getId(), saved.getEventId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatRoom> getRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoom> getRoomsByEventId(String eventId) {
        return roomRepository.findByEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoom> getRoomsByUserId(String userId) {
        return roomRepository.findByParticipantId(userId);
    }

    @Override
    @Transactional
    public ChatRoom joinRoom(String roomId, String userId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ChatException.RoomNotFoundException(roomId));
        
        if (!room.getParticipantIds().contains(userId)) {
            room.getParticipantIds().add(userId);
            roomRepository.save(room);
            
            eventPublisher.publishUserJoined(roomId, userId);
            cachePort.subscribeToRoom(roomId, userId);
            
            log.info("User {} joined room {}", userId, roomId);
        }
        
        return room;
    }

    @Override
    @Transactional
    public ChatRoom leaveRoom(String roomId, String userId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ChatException.RoomNotFoundException(roomId));
        
        room.getParticipantIds().remove(userId);
        roomRepository.save(room);
        
        eventPublisher.publishUserLeft(roomId, userId);
        cachePort.unsubscribeFromRoom(roomId, userId);
        
        log.info("User {} left room {}", userId, roomId);
        return room;
    }

    @Override
    @Transactional
    public ChatMessage sendMessage(ChatMessage message) {
        ChatRoom room = roomRepository.findById(message.getRoomId())
                .orElseThrow(() -> new ChatException.RoomNotFoundException(message.getRoomId()));
        
        if (!room.getParticipantIds().contains(message.getSenderId())) {
            throw new ChatException.UnauthorizedException("User not in this room");
        }
        
        message.setTimestamp(LocalDateTime.now());
        message.setDeleted(false);
        
        ChatMessage saved = messageRepository.save(message);
        
        room.setLastMessageAt(LocalDateTime.now());
        roomRepository.save(room);
        
        cachePort.publishMessage(message.getRoomId(), saved);
        eventPublisher.publishMessage(saved);
        
        log.info("Message sent in room {} by user {}", message.getRoomId(), message.getSenderId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesByRoom(String roomId, int page, int size) {
        return messageRepository.findByRoomId(roomId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatMessage> getMessageById(String messageId) {
        return messageRepository.findById(messageId);
    }

    @Override
    @Transactional
    public ChatMessage editMessage(String messageId, String content, String userId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ChatException.MessageNotFoundException(messageId));
        
        if (!message.getSenderId().equals(userId)) {
            throw new ChatException.UnauthorizedException("Can only edit your own messages");
        }
        
        message.setContent(content);
        message.setEditedAt(LocalDateTime.now());
        
        ChatMessage saved = messageRepository.save(message);
        cachePort.publishMessage(message.getRoomId(), saved);
        
        log.info("Message {} edited by user {}", messageId, userId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteMessage(String messageId, String userId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ChatException.MessageNotFoundException(messageId));
        
        if (!message.getSenderId().equals(userId)) {
            throw new ChatException.UnauthorizedException("Can only delete your own messages");
        }
        
        message.setDeleted(true);
        message.setContent("This message has been deleted");
        messageRepository.save(message);
        
        cachePort.publishMessage(message.getRoomId(), message);
        log.info("Message {} deleted by user {}", messageId, userId);
    }

    @Override
    @Transactional
    public void markMessagesAsRead(String roomId, String userId) {
        messageRepository.markAsRead(roomId, userId);
        log.info("Messages marked as read in room {} for user {}", roomId, userId);
    }

    @Override
    public void sendTypingIndicator(String roomId, String userId, boolean isTyping) {
        cachePort.publishTypingIndicator(roomId, userId, isTyping);
        eventPublisher.publishTypingIndicator(roomId, userId, isTyping);
    }
}
