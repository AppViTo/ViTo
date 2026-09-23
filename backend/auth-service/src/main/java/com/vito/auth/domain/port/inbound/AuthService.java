package com.vito.auth.domain.port.inbound;

import com.vito.auth.domain.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface AuthService {
    
    AuthResult register(RegisterCommand command);
    
    AuthResult login(LoginCommand command);
    
    AuthResult refreshToken(RefreshTokenCommand command);
    
    void logout(String token);
    
    void validateToken(String token);
    
    User getUserById(String userId);
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RegisterCommand {
        private String username;
        private String email;
        private String password;
        private String displayName;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LoginCommand {
        private String email;
        private String password;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RefreshTokenCommand {
        private String refreshToken;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AuthResult {
        private String accessToken;
        private String refreshToken;
        private User user;
        private Long expiresIn;
    }
}
