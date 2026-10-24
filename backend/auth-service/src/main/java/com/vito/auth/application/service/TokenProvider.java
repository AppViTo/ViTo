package com.vito.auth.application.service;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class TokenProvider {
    
    private final SecretKey key;
    private final Long accessExpiration;
    private final Long refreshExpiration;
    
    public TokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") Long accessExpiration,
            @Value("${jwt.refresh-expiration}") Long refreshExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }
    
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("roles", user.getRoles());
        
        return createToken(claims, user.getId(), accessExpiration);
    }
    
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("type", "refresh");
        
        return createToken(claims, user.getId(), refreshExpiration);
    }
    
    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                // jti único: el logout revoca el access token por este id (T-B21)
                .id(UUID.randomUUID().toString())
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    
    public String validateAccessToken(String token) {
        return parseAccessToken(token).userId();
    }

    /**
     * Valida el access token y devuelve lo necesario para revocarlo. {@code jti} es null en
     * los tokens emitidos antes de T-B21.
     */
    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = parseToken(token);

            // Check if it's an access token (not refresh)
            if (claims.containsKey("type") && "refresh".equals(claims.get("type"))) {
                throw AuthenticationException.invalidAccessToken();
            }

            Date expiration = claims.getExpiration();
            return new AccessTokenClaims(claims.getSubject(), claims.getId(),
                    expiration != null ? expiration.toInstant() : null);
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException: token vacío ("Bearer " sin valor)
            throw AuthenticationException.invalidAccessToken();
        }
    }

    /** Datos de un access token válido: sujeto, {@code jti} y vencimiento. */
    public record AccessTokenClaims(String userId, String jti, Instant expiresAt) {
    }
    
    public String validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            
            // Check if it's a refresh token
            if (!claims.containsKey("type") || !"refresh".equals(claims.get("type"))) {
                throw AuthenticationException.invalidRefreshToken();
            }
            
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw AuthenticationException.invalidRefreshToken();
        }
    }
    
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
