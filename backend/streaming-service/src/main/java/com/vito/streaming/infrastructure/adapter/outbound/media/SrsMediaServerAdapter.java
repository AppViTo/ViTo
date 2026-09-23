package com.vito.streaming.infrastructure.adapter.outbound.media;

import com.vito.streaming.domain.model.LiveStream;
import com.vito.streaming.domain.port.outbound.MediaServerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class SrsMediaServerAdapter implements MediaServerPort {
    
    @Value("${srs.api-url:http://localhost:1985}")
    private String srsApiUrl;
    
    @Value("${srs.stream-url:rtmp://localhost:1935}")
    private String srsStreamUrl;
    
    @Value("${srs.playback-url:http://localhost:8080}")
    private String srsPlaybackUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public LiveStream createStream(String streamId, LiveStream.StreamSettings settings) {
        log.info("Creating stream on SRS: {}", streamId);
        
        try {
            // SRS API call to create stream
            String url = srsApiUrl + "/api/v1/raw";
            
            String requestBody = String.format(
                    "{\"server\":\"localhost\",\"id\":\"%s\",\"action\":\"getServerConfig\"}",
                    streamId);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
            
            log.info("Stream created on SRS: {}", streamId);
            
            return LiveStream.builder()
                    .id(streamId)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create stream on SRS: {}", e.getMessage());
            throw new RuntimeException("Failed to create stream on media server", e);
        }
    }
    
    @Override
    public LiveStream startStream(String streamKey) {
        log.info("Starting stream on SRS with key: {}", streamKey);
        
        try {
            // SRS API call to start streaming
            String url = srsApiUrl + "/api/v1/raw";
            
            String requestBody = String.format(
                    "{\"server\":\"localhost\",\"id\":\"%s\",\"action\":\"getStreams\"}",
                    streamKey);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
            
            log.info("Stream started on SRS: {}", streamKey);
            
            return LiveStream.builder()
                    .streamKey(streamKey)
                    .status(LiveStream.StreamStatus.LIVE)
                    .build();
        } catch (Exception e) {
            log.error("Failed to start stream on SRS: {}", e.getMessage());
            throw new RuntimeException("Failed to start stream on media server", e);
        }
    }
    
    @Override
    public LiveStream stopStream(String streamKey) {
        log.info("Stopping stream on SRS with key: {}", streamKey);
        
        try {
            // SRS API call to stop streaming
            String url = srsApiUrl + "/api/v1/raw";
            
            String requestBody = String.format(
                    "{\"server\":\"localhost\",\"id\":\"%s\",\"action\":\"kick\"}",
                    streamKey);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
            
            log.info("Stream stopped on SRS: {}", streamKey);
            
            return LiveStream.builder()
                    .streamKey(streamKey)
                    .status(LiveStream.StreamStatus.IDLE)
                    .build();
        } catch (Exception e) {
            log.error("Failed to stop stream on SRS: {}", e.getMessage());
            throw new RuntimeException("Failed to stop stream on media server", e);
        }
    }
    
    @Override
    public Optional<String> getStreamStatus(String streamKey) {
        try {
            String url = srsApiUrl + "/api/v1/raw";
            
            String requestBody = String.format(
                    "{\"server\":\"localhost\",\"id\":\"%s\",\"action\":\"getStreams\"}",
                    streamKey);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
            
            // Parse response to get status
            if (response.getStatusCode().is2xxSuccessful()) {
                return Optional.of("active");
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get stream status from SRS: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public String generateStreamKey(String eventId, String userId) {
        return String.format("vito_%s_%s_%s", eventId, userId, UUID.randomUUID().toString().substring(0, 8));
    }
    
    @Override
    public String getPlaybackUrl(String streamKey) {
        // Convert RTMP stream key to HTTP-FLV or HLS playback URL
        return String.format("%s/live/%s.flv", srsPlaybackUrl, streamKey);
    }
    
    @Override
    public boolean validateStreamKey(String streamKey) {
        return streamKey != null && streamKey.startsWith("vito_");
    }
}
