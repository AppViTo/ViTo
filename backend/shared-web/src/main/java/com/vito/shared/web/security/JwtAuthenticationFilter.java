package com.vito.shared.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT de los servicios de recursos (T-B18, ADR-017): valida la firma HS del access token
 * con {@code jwt.secret} y deja en el contexto un {@link AuthenticatedUser} con el {@code sub}
 * y el nombre visible (R6). Sin token o con uno inválido, sigue sin autenticación y Spring
 * Security responde 401 en las rutas protegidas.
 *
 * <p>Cada servicio lo trae con {@code @Import(JwtAuthenticationFilter.class)} en su
 * {@code SecurityConfig}. No lo usan api-gateway (filtro reactivo propio, con revocación) ni
 * auth-service (valida con su {@code TokenProvider}, porque emite los tokens).
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String USER_NAME_HEADER = "X-User-Name";
        
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = authHeader.substring(7);
        
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String userId = claims.getSubject();

            if (userId != null) {
                // Nombre visible (R6): el claim firmado; si el token aún no lo trae, el header del gateway
                String name = claims.get("name", String.class);
                AuthenticatedUser user = new AuthenticatedUser(userId,
                        name != null ? name : request.getHeader(USER_NAME_HEADER));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("USER"))
                        );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
        
        filterChain.doFilter(request, response);
    }
}
