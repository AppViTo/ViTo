package com.vito.checkin.domain.port.outbound;

import com.vito.checkin.domain.model.CheckInEvent.UserProfile;

import java.util.Optional;

public interface UserProfileService {
    
    Optional<UserProfile> getUserProfile(String userId);
}
