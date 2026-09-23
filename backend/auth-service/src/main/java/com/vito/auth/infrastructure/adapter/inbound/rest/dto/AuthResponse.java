package com.vito.auth.infrastructure.adapter.inbound.rest.dto;

import com.vito.auth.domain.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private String accessToken;
    
    private String refreshToken;
    
    private UserInfo user;
    
    private Long expiresIn;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String displayName;
        private String avatar;
        
        public static UserInfo fromUser(User user) {
            return UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .displayName(user.getProfile() != null ? user.getProfile().getDisplayName() : null)
                    .avatar(user.getProfile() != null ? user.getProfile().getAvatar() : null)
                    .build();
        }
    }
}
