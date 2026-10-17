package com.vito.auth.infrastructure.adapter.inbound.rest;

import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.inbound.AuthService;
import com.vito.auth.domain.port.inbound.AuthService.*;
import com.vito.auth.infrastructure.adapter.inbound.rest.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        
        AuthResult result = authService.register(RegisterCommand.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .displayName(request.getDisplayName())
                .build());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.builder()
                        .accessToken(result.getAccessToken())
                        .refreshToken(result.getRefreshToken())
                        .user(AuthResponse.UserInfo.fromUser(result.getUser()))
                        .expiresIn(result.getExpiresIn())
                        .build());
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        
        AuthResult result = authService.login(LoginCommand.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .build());
        
        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(result.getAccessToken())
                .refreshToken(result.getRefreshToken())
                .user(AuthResponse.UserInfo.fromUser(result.getUser()))
                .expiresIn(result.getExpiresIn())
                .build());
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request");
        
        AuthResult result = authService.refreshToken(RefreshTokenCommand.builder()
                .refreshToken(request.getRefreshToken())
                .build());
        
        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(result.getAccessToken())
                .refreshToken(result.getRefreshToken())
                .user(AuthResponse.UserInfo.fromUser(result.getUser()))
                .expiresIn(result.getExpiresIn())
                .build());
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        authService.logout(token);
        // logoutUser: 200 sin cuerpo (contrato), no 204
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = authentication.getName();
        User user = authService.getUserById(userId);
        
        return ResponseEntity.ok(AuthResponse.UserInfo.fromUser(user));
    }
    
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        authService.validateToken(token);
        return ResponseEntity.ok().build();
    }
}
