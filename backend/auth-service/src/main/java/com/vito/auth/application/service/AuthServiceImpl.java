package com.vito.auth.application.service;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.exception.UserAlreadyExistsException;
import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.inbound.AuthService;
import com.vito.auth.domain.port.outbound.CachePort;
import com.vito.auth.domain.port.outbound.EventPublisherPort;
import com.vito.auth.domain.port.outbound.SecurityAuditPort;
import com.vito.auth.domain.port.outbound.SecurityAuditPort.SecurityEvent;
import com.vito.auth.domain.port.outbound.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CachePort cachePort;
    private final EventPublisherPort eventPublisher;
    private final TokenProvider tokenProvider;
    private final SecurityAuditPort audit;
    
    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    
    @Override
    public AuthResult register(RegisterCommand command) {
        // Principio VI: sin el correo en los logs
        log.info("Registering new user");
        
        // Validate unique constraints
        if (userRepository.existsByUsername(command.getUsername())) {
            throw UserAlreadyExistsException.usernameTaken();
        }
        
        if (userRepository.existsByEmail(command.getEmail())) {
            throw UserAlreadyExistsException.emailTaken();
        }
        
        // Create user
        User user = User.builder()
                .username(command.getUsername())
                .email(command.getEmail())
                .passwordHash(passwordEncoder.encode(command.getPassword()))
                .profile(User.Profile.builder()
                        .displayName(command.getDisplayName())
                        .build())
                .stats(User.Stats.builder().build())
                .settings(User.Settings.builder()
                        .notifications(User.NotificationSettings.builder().build())
                        .build())
                .roles(List.of("USER"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        User savedUser = userRepository.save(user);
        
        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(savedUser);
        String refreshToken = tokenProvider.generateRefreshToken(savedUser);
        
        // Store refresh token in cache
        cachePort.set("refresh_token:" + savedUser.getId(), refreshToken, 
                Duration.ofMillis(jwtExpiration * 7));
        
        // Publish event
        eventPublisher.publish("user-events", savedUser.getId(), 
                new UserRegisteredEvent(savedUser.getId(), savedUser.getEmail()));
        
        audit.record(SecurityEvent.REGISTER, savedUser.getId());
        log.info("User registered successfully: {}", savedUser.getId());
        
        return AuthResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(savedUser)
                .expiresIn(expiresInSeconds())
                .build();
    }
    
    @Override
    public AuthResult login(LoginCommand command) {
        log.info("Login attempt");
        
        User user = userRepository.findByEmail(command.getEmail()).orElse(null);
        if (user == null) {
            audit.record(SecurityEvent.LOGIN_FAILURE, null);
            throw AuthenticationException.invalidCredentials();
        }
        
        if (!passwordEncoder.matches(command.getPassword(), user.getPasswordHash())) {
            audit.record(SecurityEvent.LOGIN_FAILURE, user.getId());
            throw AuthenticationException.invalidCredentials();
        }
        
        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        
        // Store refresh token in cache
        cachePort.set("refresh_token:" + user.getId(), refreshToken, 
                Duration.ofMillis(jwtExpiration * 7));
        
        audit.record(SecurityEvent.LOGIN_SUCCESS, user.getId());
        log.info("User logged in successfully: {}", user.getId());
        
        return AuthResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user)
                .expiresIn(expiresInSeconds())
                .build();
    }
    
    @Override
    public AuthResult refreshToken(RefreshTokenCommand command) {
        log.info("Refreshing token");
        
        String userId;
        try {
            userId = tokenProvider.validateRefreshToken(command.getRefreshToken());
        } catch (AuthenticationException e) {
            audit.record(SecurityEvent.REFRESH_REJECTED, null);
            throw e;
        }
        
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            audit.record(SecurityEvent.REFRESH_REJECTED, userId);
            throw AuthenticationException.invalidRefreshToken();
        }
        
        // Check if refresh token is blacklisted (reutilizar un refresh ya rotado)
        if (cachePort.isBlacklisted(command.getRefreshToken())) {
            audit.record(SecurityEvent.REFRESH_REJECTED, userId);
            throw AuthenticationException.invalidRefreshToken();
        }
        
        // Blacklist old refresh token
        cachePort.addToBlacklist(command.getRefreshToken(), Duration.ofMillis(jwtExpiration));
        
        // Generate new tokens
        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);
        
        // Store new refresh token in cache
        cachePort.set("refresh_token:" + user.getId(), newRefreshToken, 
                Duration.ofMillis(jwtExpiration * 7));
        
        return AuthResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(user)
                .expiresIn(expiresInSeconds())
                .build();
    }
    
    @Override
    public void logout(String token) {
        log.info("Logging out user");
        
        TokenProvider.AccessTokenClaims claims = tokenProvider.parseAccessToken(token);
        String userId = claims.userId();

        // T-B21: el gateway rechaza el jti revocado; la clave vive lo que le queda al token
        if (claims.jti() != null && claims.expiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(), claims.expiresAt());
            if (!remaining.isNegative() && !remaining.isZero()) {
                cachePort.revokeAccessToken(claims.jti(), remaining);
            }
        } else {
            // Token emitido antes de T-B21: no se puede revocar, vence solo
            log.info("Access token without jti for user {}; it stays valid until it expires", userId);
        }

        // Remove refresh token from cache
        cachePort.delete("refresh_token:" + userId);
        
        audit.record(SecurityEvent.LOGOUT, userId);
        log.info("User logged out successfully: {}", userId);
    }
    
    @Override
    public void validateToken(String token) {
        tokenProvider.validateAccessToken(token);
    }
    
    @Override
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(AuthenticationException::userNotFound);
    }
    
    /** ADR-004: segundos hasta que expira el access token; jwt.expiration está en milisegundos. */
    private long expiresInSeconds() {
        return Duration.ofMillis(jwtExpiration).toSeconds();
    }
    
    public record UserRegisteredEvent(String userId, String email) {}
}
