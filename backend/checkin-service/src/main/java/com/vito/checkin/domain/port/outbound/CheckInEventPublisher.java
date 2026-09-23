package com.vito.checkin.domain.port.outbound;

import com.vito.checkin.domain.model.CheckInEvent;

public interface CheckInEventPublisher {
    
    void publishCheckIn(CheckInEvent event);
    
    void publishCheckInStats(String eventId, int totalCheckedIn, int totalExpected);
}
