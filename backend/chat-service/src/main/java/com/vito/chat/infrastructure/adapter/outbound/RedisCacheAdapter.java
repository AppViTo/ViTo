package com.vito.chat.infrastructure.adapter.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.port.outbound.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CHAT_CHANNEL = "chat:room:";
    private static final String TYPING_CHANNEL = "chat:typing:";
    private static final String ONLINE_USERS_KEY = "chat:online:";

    @Override
    public void publishMessage(String roomId, ChatMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHAT_CHANNEL + roomId, json);
            log.debug("Published message to Redis channel {}: {}", roomId, message.getId());
        } catch (Exception e) {
            log.error("Failed to publish message to Redis: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishTypingIndicator(String roomId, String userId, boolean isTyping) {
        try {
            String event = String.format("{\"userId\":\"%s\",\"isTyping\":%s}", userId, isTyping);
            redisTemplate.convertAndSend(TYPING_CHANNEL + roomId, event);
            log.debug("Published typing indicator to Redis: room={}, user={}", roomId, userId);
        } catch (Exception e) {
            log.error("Failed to publish typing indicator to Redis: {}", e.getMessage(), e);
        }
    }

    @Override
    public void subscribeToRoom(String roomId, String userId) {
        String key = ONLINE_USERS_KEY + roomId;
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        log.debug("User {} subscribed to room {}", userId, roomId);
    }

    @Override
    public void unsubscribeFromRoom(String roomId, String userId) {
        String key = ONLINE_USERS_KEY + roomId;
        redisTemplate.opsForSet().remove(key, userId);
        log.debug("User {} unsubscribed from room {}", userId, roomId);
    }

    @Override
    public List<String> getOnlineUsers(String roomId) {
        String key = ONLINE_USERS_KEY + roomId;
        Set<Object> members = redisTemplate.opsForSet().members(key);
        return members != null ? members.stream().map(Object::toString).toList() : List.of();
    }
}
