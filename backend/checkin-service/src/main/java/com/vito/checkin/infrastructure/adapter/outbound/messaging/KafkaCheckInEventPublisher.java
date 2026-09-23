package com.vito.checkin.infrastructure.adapter.outbound.messaging;

import com.vito.checkin.domain.model.CheckInEvent;
import com.vito.checkin.domain.port.outbound.CheckInEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCheckInEventPublisher implements CheckInEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Override
    public void publishCheckIn(CheckInEvent event) {
        Map<String, Object> payload = Map.of(
                "type", "USER_CHECKED_IN",
                "eventId", event.getEventId(),
                "userId", event.getUserId(),
                "username", event.getUsername(),
                "displayName", event.getDisplayName(),
                "checkedInAt", event.getCheckedInAt().toString(),
                "totalCheckedIn", event.getTotalCheckedIn(),
                "timestamp", System.currentTimeMillis()
        );
        
        kafkaTemplate.send("checkin-events", event.getEventId(), payload);
        log.info("Published check-in event for user: {} in event: {}", 
                event.getUserId(), event.getEventId());
    }
    
    @Override
    public void publishCheckInStats(String eventId, int totalCheckedIn, int totalExpected) {
        Map<String, Object> payload = Map.of(
                "type", "CHECK_IN_STATS_UPDATE",
                "eventId", eventId,
                "totalCheckedIn", totalCheckedIn,
                "totalExpected", totalExpected,
                "checkInRate", totalExpected > 0 ? 
                        (double) totalCheckedIn / totalExpected * 100 : 0,
                "timestamp", System.currentTimeMillis()
        );
        
        kafkaTemplate.send("checkin-events", eventId, payload);
        log.info("Published check-in stats for event: {}", eventId);
    }
}
