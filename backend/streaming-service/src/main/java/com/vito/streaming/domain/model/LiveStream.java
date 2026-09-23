package com.vito.streaming.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveStream {
    
    private String id;
    
    private String eventId;
    
    private String organizerId;
    
    private String title;
    
    private String description;
    
    private StreamStatus status;
    
    private StreamSettings settings;
    
    private StreamStats stats;
    
    private String streamKey;
    
    private String playbackUrl;
    
    private List<String> thumbnailUrls;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime endedAt;
    
    private LocalDateTime createdAt;
    
    public enum StreamStatus {
        IDLE,
        CONNECTING,
        LIVE,
        BUFFERING,
        ENDED,
        ERROR
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamSettings {
        private boolean enableChat;
        private boolean enableReactions;
        private boolean enableRecording;
        private int maxViewers;
        private int chatSlowMode;
        private List<String> allowedRegions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamStats {
        private int currentViewers;
        private int totalViewers;
        private int peakViewers;
        private int likesCount;
        private int commentsCount;
        private int sharesCount;
        private long duration;
    }
    
    public boolean isLive() {
        return status == StreamStatus.LIVE;
    }
    
    public void start() {
        this.status = StreamStatus.LIVE;
        this.startedAt = LocalDateTime.now();
    }
    
    public void end() {
        this.status = StreamStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }
}
