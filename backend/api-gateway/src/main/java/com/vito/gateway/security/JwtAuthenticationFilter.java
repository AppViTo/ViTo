package com.vito.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /**
     * Atributo del exchange con el userId que el gateway autenticó. Lo usa el rate limiter:
     * nunca debe leerse la identidad de un header que pudo mandar el cliente.
     */
    public static final String AUTHENTICATED_USER_ATTR = "vito.authenticatedUserId";

    /** Headers de identidad de ADR-007: solo el gateway puede ponerlos. */
    static final List<String> USER_HEADERS = List.of("X-User-Id", "X-User-Email", "X-User-Roles");

    /** Rutas WebSocket (ADR-006): el token llega en {@code ?token=} porque el navegador no envía headers. */
    static final String WS_PATH_PREFIX = "/ws/";
    static final String WS_TOKEN_PARAM = "token";
    static final CloseStatus WS_UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");

    private final String jwtSecret;
    private final WebSocketService webSocketService;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String jwtSecret, WebSocketService webSocketService) {
        this.jwtSecret = jwtSecret;
        this.webSocketService = webSocketService;
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Los X-User-* que manda el cliente se descartan en todas las rutas, públicas incluidas
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> USER_HEADERS.forEach(headers::remove))
                .build();
        exchange = exchange.mutate().request(request).build();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        boolean webSocket = path.startsWith(WS_PATH_PREFIX);
        String token = webSocket ? webSocketToken(request) : bearerToken(request);
        if (token == null) {
            return reject(exchange, webSocket, "Missing or invalid Authorization header");
        }

        try {
            Claims claims = validateToken(token);
            exchange.getAttributes().put(AUTHENTICATED_USER_ATTR, claims.getSubject());

            // Add user info to headers for downstream services
            ServerHttpRequest.Builder modifiedRequest = request.mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Email", claims.get("email", String.class))
                    .header("X-User-Roles", rolesHeader(claims.get("roles")));
            if (webSocket) {
                // El servicio recibe la identidad por X-User-*; el token no viaja más allá del gateway
                modifiedRequest.uri(withoutTokenParam(request.getURI()));
            }

            return chain.filter(exchange.mutate().request(modifiedRequest.build()).build());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token for {}: {}", path, e.getMessage());
            return reject(exchange, webSocket, "Invalid or expired token");
        }
    }

    private static String bearerToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
    }

    /** {@code ?token=} según CONVENTIONS §7; el header Bearer se acepta para clientes que sí pueden enviarlo. */
    private static String webSocketToken(ServerHttpRequest request) {
        String token = request.getQueryParams().getFirst(WS_TOKEN_PARAM);
        return token != null && !token.isBlank() ? token : bearerToken(request);
    }

    static URI withoutTokenParam(URI uri) {
        return UriComponentsBuilder.fromUri(uri).replaceQueryParam(WS_TOKEN_PARAM).build(true).toUri();
    }

    private Mono<Void> reject(ServerWebExchange exchange, boolean webSocket, String message) {
        if (webSocket && "websocket".equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade())) {
            // El navegador no expone el status HTTP de un handshake fallido: se acepta y se cierra con 4401
            return webSocketService.handleRequest(exchange, session -> session.close(WS_UNAUTHORIZED));
        }
        return unauthorizedResponse(exchange, message);
    }

    /**
     * auth-service emite "roles" como lista JSON (["USER"]); ADR-007 define X-User-Roles
     * como valores separados por comas ("USER,ORGANIZER").
     */
    static String rolesHeader(Object roles) {
        if (roles == null) {
            return "";
        }
        if (roles instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return roles.toString();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = String.format("{\"error\":\"%s\",\"status\":401}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Run before other filters
    }
}
