package com.vito.streaming.application.service;

import com.vito.streaming.domain.exception.StreamingException;
import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.LiveStream;
import com.vito.streaming.domain.model.Reaction;
import com.vito.streaming.domain.port.inbound.StreamingService;
import com.vito.streaming.domain.port.outbound.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingServiceImpl implements StreamingService {
    
    private final LiveStreamRepository streamRepository;
    private final ReactionRepository reactionRepository;
    private final ChatRepository chatRepository;
    private final MediaServerPort mediaServerPort;
    private final StreamingEventPublisher eventPublisher;
    
    @Override
    public LiveStream createStream(CreateStreamCommand command) {
        log.info("Creating stream for event: {} by user: {}", command.getEventId(), command.getOrganizerId());
        
        // Generate stream key
        String streamKey = mediaServerPort.generateStreamKey(command.getEventId(), command.getOrganizerId());
        String playbackUrl = mediaServerPort.getPlaybackUrl(streamKey);
        
        LiveStream stream = LiveStream.builder()
                .eventId(command.getEventId())
                .organizerId(command.getOrganizerId())
                .title(command.getTitle())
                .description(command.getDescription())
                .status(LiveStream.StreamStatus.IDLE)
                .settings(command.getSettings() != null ? command.getSettings() :
                        LiveStream.StreamSettings.builder()
                                .enableChat(true)
                                .enableReactions(true)
                                .enableRecording(true)
                                .maxViewers(1000)
                                .chatSlowMode(0)
                                .build())
                .stats(LiveStream.StreamStats.builder()
                        .currentViewers(0)
                        .totalViewers(0)
                        .peakViewers(0)
                        .likesCount(0)
                        .commentsCount(0)
                        .sharesCount(0)
                        .duration(0)
                        .build())
                .streamKey(streamKey)
                .playbackUrl(playbackUrl)
                .createdAt(LocalDateTime.now())
                .build();
        
        LiveStream savedStream = streamRepository.save(stream);
        
        log.info("Stream created: {} with key: {}", savedStream.getId(), streamKey);
        
        return savedStream;
    }
    
    @Override
    public LiveStream startStream(String streamId, String userId) {
        log.info("Starting stream: {} by user: {}", streamId, userId);
        
        LiveStream stream = streamRepository.findById(streamId)
                .orElseThrow(StreamingException::streamNotFound);
        
        if (!stream.getOrganizerId().equals(userId)) {
            throw StreamingException.notAuthorized();
        }
        
        if (stream.isLive()) {
            throw StreamingException.streamAlreadyLive();
        }
        
        // Start stream on media server
        LiveStream startedStream = mediaServerPort.startStream(stream.getStreamKey());
        
        stream.start();
        LiveStream savedStream = streamRepository.save(stream);
        
        eventPublisher.publishStreamStarted(streamId, stream.getEventId());
        
        log.info("Stream started: {}", streamId);
        
        return savedStream;
    }
    
    @Override
    public LiveStream stopStream(String streamId, String userId) {
        log.info("Stopping stream: {} by user: {}", streamId, userId);
        
        LiveStream stream = streamRepository.findById(streamId)
                .orElseThrow(StreamingException::streamNotFound);
        
        if (!stream.getOrganizerId().equals(userId)) {
            throw StreamingException.notAuthorized();
        }
        
        if (!stream.isLive()) {
            throw StreamingException.streamNotLive();
        }
        
        // Stop stream on media server
        mediaServerPort.stopStream(stream.getStreamKey());
        
        stream.end();
        LiveStream savedStream = streamRepository.save(stream);
        
        eventPublisher.publishStreamEnded(streamId, stream.getEventId());
        
        log.info("Stream ended: {}", streamId);
        
        return savedStream;
    }
    
    @Override
    public LiveStream getStream(String streamId) {
        return streamRepository.findById(streamId)
                .orElseThrow(StreamingException::streamNotFound);
    }
    
    @Override
    public LiveStream getStreamByEventId(String eventId) {
        return streamRepository.findByEventId(eventId)
                .orElseThrow(StreamingException::streamNotFound);
    }
    
    @Override
    public List<LiveStream> getLiveStreams(int page, int size) {
        return streamRepository.findLiveStreams(page, size);
    }
    
    @Override
    public Reaction addReaction(AddReactionCommand command) {
        log.info("Adding reaction to stream: {} by user: {}", command.getStreamId(), command.getUserId());
        
        LiveStream stream = streamRepository.findById(command.getStreamId())
                .orElseThrow(StreamingException::streamNotFound);
        
        if (!stream.isLive()) {
            throw StreamingException.streamNotLive();
        }
        
        if (!stream.getSettings().isEnableReactions()) {
            throw StreamingException.reactionsDisabled();
        }
        
        Reaction reaction;
        
        switch (command.getType()) {
            case HEART -> reaction = Reaction.createHeart(
                    command.getStreamId(), command.getUserId(), command.getUsername(),
                    command.getDisplayName(), command.getAvatar());
            case FIRE -> reaction = Reaction.createFire(
                    command.getStreamId(), command.getUserId(), command.getUsername(),
                    command.getDisplayName(), command.getAvatar());
            default -> reaction = Reaction.createLike(
                    command.getStreamId(), command.getUserId(), command.getUsername(),
                    command.getDisplayName(), command.getAvatar());
        }
        
        if (command.getEmoji() != null) {
            reaction.setEmoji(command.getEmoji());
        }
        
        Reaction savedReaction = reactionRepository.save(reaction);
        
        // Update stream stats
        stream.getStats().setLikesCount(stream.getStats().getLikesCount() + 1);
        streamRepository.save(stream);
        
        eventPublisher.publishReaction(command.getStreamId(), savedReaction);
        
        return savedReaction;
    }
    
    @Override
    public List<Reaction> getReactions(String streamId, int page, int size) {
        return reactionRepository.findByStreamId(streamId, page, size);
    }
    
    @Override
    public ChatMessage sendChatMessage(SendChatMessageCommand command) {
        log.info("Sending chat message to stream: {} by user: {}", command.getStreamId(), command.getUserId());
        
        LiveStream stream = streamRepository.findById(command.getStreamId())
                .orElseThrow(StreamingException::streamNotFound);
        
        if (!stream.isLive()) {
            throw StreamingException.streamNotLive();
        }
        
        if (!stream.getSettings().isEnableChat()) {
            throw StreamingException.chatDisabled();
        }
        
        ChatMessage message = ChatMessage.createNormal(
                command.getStreamId(), command.getUserId(), command.getUsername(),
                command.getDisplayName(), command.getAvatar(), command.getMessage());
        
        ChatMessage savedMessage = chatRepository.save(message);
        
        // Update stream stats
        stream.getStats().setCommentsCount(stream.getStats().getCommentsCount() + 1);
        streamRepository.save(stream);
        
        eventPublisher.publishChatMessage(command.getStreamId(), savedMessage);
        
        return savedMessage;
    }
    
    @Override
    public List<ChatMessage> getChatMessages(String streamId, int page, int size) {
        return chatRepository.findByStreamId(streamId, page, size);
    }
    
    @Override
    public StreamInfo getStreamInfo(String streamId) {
        LiveStream stream = streamRepository.findById(streamId)
                .orElseThrow(StreamingException::streamNotFound);
        
        long totalReactions = reactionRepository.countByStreamId(streamId);
        long totalMessages = chatRepository.countByStreamId(streamId);
        
        long duration = 0;
        if (stream.getStartedAt() != null) {
            LocalDateTime endTime = stream.getEndedAt() != null ? stream.getEndedAt() : LocalDateTime.now();
            duration = java.time.Duration.between(stream.getStartedAt(), endTime).getSeconds();
        }
        
        return StreamInfo.builder()
                .stream(stream)
                .currentViewers(stream.getStats().getCurrentViewers())
                .totalReactions((int) totalReactions)
                .totalMessages((int) totalMessages)
                .duration(duration)
                .build();
    }
}
