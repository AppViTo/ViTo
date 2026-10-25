package com.vito.auth.application.service;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenProviderTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-256-bits-long";
    private static final long ACCESS_MS = Duration.ofMinutes(15).toMillis();

    private final TokenProvider provider = new TokenProvider(SECRET, ACCESS_MS, Duration.ofDays(7).toMillis());

    private final User user = User.builder()
            .id("user123")
            .username("testuser")
            .email("test@example.com")
            .roles(List.of("USER"))
            .build();

    @Test
    void accessTokenCarriesUniqueJtiAndExpiration() {
        TokenProvider.AccessTokenClaims first = provider.parseAccessToken(provider.generateAccessToken(user));
        TokenProvider.AccessTokenClaims second = provider.parseAccessToken(provider.generateAccessToken(user));

        assertEquals("user123", first.userId());
        assertNotNull(first.jti());
        assertNotEquals(first.jti(), second.jti());
        Duration remaining = Duration.between(Instant.now(), first.expiresAt());
        assertTrue(remaining.compareTo(Duration.ofMinutes(15)) <= 0, remaining.toString());
        assertTrue(remaining.compareTo(Duration.ofMinutes(14)) > 0, remaining.toString());
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refresh = provider.generateRefreshToken(user);

        assertThrows(AuthenticationException.class, () -> provider.parseAccessToken(refresh));
    }
}
