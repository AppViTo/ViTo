package com.vito.auth.application.service;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.exception.UserAlreadyExistsException;
import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.inbound.AuthService.LoginCommand;
import com.vito.auth.domain.port.inbound.AuthService.RefreshTokenCommand;
import com.vito.auth.domain.port.inbound.AuthService.RegisterCommand;
import com.vito.auth.domain.port.inbound.AuthService.AuthResult;
import com.vito.auth.domain.port.outbound.CachePort;
import com.vito.auth.domain.port.outbound.EventPublisherPort;
import com.vito.auth.domain.port.outbound.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CachePort cachePort;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // @Value no se inyecta con @InjectMocks
        ReflectionTestUtils.setField(authService, "jwtExpiration", 900_000L);
        testUser = User.builder()
                .id("user123")
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$10$encodedPassword")
                .profile(User.Profile.builder()
                        .displayName("Test User")
                        .build())
                .stats(User.Stats.builder().build())
                .settings(User.Settings.builder()
                        .notifications(User.NotificationSettings.builder().build())
                        .build())
                .build();
    }

    @Test
    void register_Success() {
        // Arrange
        RegisterCommand command = RegisterCommand.builder()
                .username("newuser")
                .email("new@example.com")
                .password("Password123")
                .displayName("New User")
                .build();

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        // Act
        AuthResult result = authService.register(command);

        // Assert
        assertNotNull(result);
        assertEquals("access_token", result.getAccessToken());
        assertEquals("refresh_token", result.getRefreshToken());
        assertEquals(testUser, result.getUser());
        assertEquals(900L, result.getExpiresIn(), "ADR-004: segundos (jwt.expiration = 900000 ms)");

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("new@example.com");
        verify(passwordEncoder).encode("Password123");
        verify(userRepository).save(any(User.class));
        verify(cachePort).set(eq("refresh_token:user123"), eq("refresh_token"), any(Duration.class));
        verify(eventPublisher).publish(eq("user-events"), eq("user123"), any());
    }

    @Test
    void register_UsernameAlreadyExists_ThrowsException() {
        // Arrange
        RegisterCommand command = RegisterCommand.builder()
                .username("existinguser")
                .email("new@example.com")
                .password("Password123")
                .build();

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // Act & Assert
        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> authService.register(command)
        );
        assertEquals("El nombre de usuario ya está en uso", exception.getMessage());

        verify(userRepository).existsByUsername("existinguser");
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_EmailAlreadyExists_ThrowsException() {
        // Arrange
        RegisterCommand command = RegisterCommand.builder()
                .username("newuser")
                .email("existing@example.com")
                .password("Password123")
                .build();

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> authService.register(command)
        );
        assertEquals("El email ya está registrado", exception.getMessage());

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("existing@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_Success() {
        // Arrange
        LoginCommand command = LoginCommand.builder()
                .email("test@example.com")
                .password("Password123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123", testUser.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        // Act
        AuthResult result = authService.login(command);

        // Assert
        assertNotNull(result);
        assertEquals("access_token", result.getAccessToken());
        assertEquals("refresh_token", result.getRefreshToken());
        assertEquals(testUser, result.getUser());
        assertEquals(900L, result.getExpiresIn());

        verify(userRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("Password123", testUser.getPasswordHash());
        verify(cachePort).set(eq("refresh_token:user123"), eq("refresh_token"), any(Duration.class));
    }

    @Test
    void refreshToken_Success_ExpiresInSeconds() {
        when(tokenProvider.validateRefreshToken("old_refresh")).thenReturn("user123");
        when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
        when(cachePort.isBlacklisted("old_refresh")).thenReturn(false);
        when(tokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        AuthResult result = authService.refreshToken(RefreshTokenCommand.builder()
                .refreshToken("old_refresh")
                .build());

        assertEquals("access_token", result.getAccessToken());
        assertEquals(900L, result.getExpiresIn());
        verify(cachePort).addToBlacklist(eq("old_refresh"), any(Duration.class));
    }

    @Test
    void login_InvalidEmail_ThrowsException() {
        // Arrange
        LoginCommand command = LoginCommand.builder()
                .email("nonexistent@example.com")
                .password("Password123")
                .build();

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                AuthenticationException.class,
                () -> authService.login(command)
        );

        verify(userRepository).findByEmail("nonexistent@example.com");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        // Arrange
        LoginCommand command = LoginCommand.builder()
                .email("test@example.com")
                .password("WrongPassword")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", testUser.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThrows(
                AuthenticationException.class,
                () -> authService.login(command)
        );

        verify(userRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("WrongPassword", testUser.getPasswordHash());
        verify(cachePort, never()).set(any(), any(), any());
    }

    @Test
    void logout_Success() {
        // Arrange
        String token = "valid_token";
        when(tokenProvider.validateAccessToken(token)).thenReturn("user123");

        // Act
        authService.logout(token);

        // Assert
        verify(tokenProvider).validateAccessToken(token);
        verify(cachePort).addToBlacklist(eq(token), any(Duration.class));
        verify(cachePort).delete("refresh_token:user123");
    }
}
