package com.vito.gateway.security;

import com.vito.gateway.error.ErrorResponses;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
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
    static final List<String> USER_HEADERS = List.of("X-User-Id", "X-User-Email", "X-User-Roles", "X-User-Name");

    /** Nombre visible (R6): claim {@code name} del access token, reenviado como {@code X-User-Name}. */
    static final String NAME_CLAIM = "name";
    static final int MAX_NAME_LENGTH = 100;

    /** Rutas WebSocket (ADR-006): el token llega en {@code ?token=} porque el navegador no envía headers. */
    static final String WS_PATH_PREFIX = "/ws/";
    static final String WS_TOKEN_PARAM = "token";
    static final CloseStatus WS_UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");

    static final String MISSING_TOKEN_MESSAGE = "Debes iniciar sesión para acceder a este recurso";
    static final String INVALID_TOKEN_MESSAGE = "Tu sesión no es válida o expiró. Inicia sesión de nuevo";

    private final String jwtSecret;
    private final WebSocketService webSocketService;
    private final RevokedAccessTokens revokedAccessTokens;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String jwtSecret,
                                   WebSocketService webSocketService,
                                   RevokedAccessTokens revokedAccessTokens) {
        this.jwtSecret = jwtSecret;
        this.webSocketService = webSocketService;
        this.revokedAccessTokens = revokedAccessTokens;
    }

    /**
     * Operaciones con {@code security: []} en openapi.yaml (R5): método y patrón exactos. Antes era
     * un {@code startsWith} por ruta, que con {@code /api/v1/invite-links/} habría dejado pública
     * también {@code POST .../accept}.
     */
    record PublicRoute(HttpMethod method, PathPattern pattern) {
        static PublicRoute of(HttpMethod method, String pattern) {
            return new PublicRoute(method, PathPatternParser.defaultInstance.parse(pattern));
        }

        boolean matches(HttpMethod requestMethod, PathContainer path) {
            return method.equals(requestMethod) && pattern.matches(path);
        }
    }

    static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            PublicRoute.of(HttpMethod.POST, "/api/v1/auth/login"),
            PublicRoute.of(HttpMethod.POST, "/api/v1/auth/register"),
            PublicRoute.of(HttpMethod.POST, "/api/v1/auth/refresh"),
            // Vista previa de un enlace de invitación: un solo segmento tras invite-links
            PublicRoute.of(HttpMethod.GET, "/api/v1/invite-links/{token}")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Los X-User-* que manda el cliente se descartan en todas las rutas, públicas incluidas
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> USER_HEADERS.forEach(headers::remove))
                .build();
        ServerWebExchange sanitized = exchange.mutate().request(request).build();
        String path = request.getURI().getPath();

        if (isPublic(request)) {
            return chain.filter(sanitized);
        }

        boolean webSocket = path.startsWith(WS_PATH_PREFIX);
        String token = webSocket ? webSocketToken(request) : bearerToken(request);
        if (token == null) {
            return reject(sanitized, webSocket, MISSING_TOKEN_MESSAGE);
        }

        Claims claims;
        try {
            claims = validateToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token for {}: {}", InviteLinkTokenRedaction.redact(path), e.getMessage());
            return reject(sanitized, webSocket, INVALID_TOKEN_MESSAGE);
        }

        // T-B21: un token con firma válida pero revocado en logout recibe el mismo 401 / 4401.
        // Los tokens sin jti (emitidos antes de T-B21) no se pueden revocar y vencen solos.
        String jti = claims.getId();
        Mono<Boolean> revoked = jti == null ? Mono.just(false) : revokedAccessTokens.isRevoked(jti);
        return revoked.flatMap(isRevoked -> {
            if (isRevoked) {
                log.debug("Rejected revoked token for {}", InviteLinkTokenRedaction.redact(path));
                return reject(sanitized, webSocket, INVALID_TOKEN_MESSAGE);
            }
            return forwardAuthenticated(sanitized, chain, claims, webSocket);
        });
    }

    private static Mono<Void> forwardAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain,
                                                   Claims claims, boolean webSocket) {
        ServerHttpRequest request = exchange.getRequest();
        exchange.getAttributes().put(AUTHENTICATED_USER_ATTR, claims.getSubject());

        // Add user info to headers for downstream services
        ServerHttpRequest.Builder modifiedRequest = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Email", claims.get("email", String.class))
                .header("X-User-Roles", rolesHeader(claims.get("roles")));
        String name = nameHeader(claims.get(NAME_CLAIM));
        if (name != null) {
            modifiedRequest.header("X-User-Name", name);
        }
        if (webSocket) {
            // El servicio recibe la identidad por X-User-*; el token no viaja más allá del gateway
            modifiedRequest.uri(withoutTokenParam(request.getURI()));
        }

        return chain.filter(exchange.mutate().request(modifiedRequest.build()).build());
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
        return ErrorResponses.write(exchange.getResponse(), HttpStatus.UNAUTHORIZED, ErrorResponses.UNAUTHORIZED, message);
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

    static boolean isPublic(ServerHttpRequest request) {
        PathContainer path = PathContainer.parsePath(request.getURI().getRawPath());
        return PUBLIC_ROUTES.stream().anyMatch(route -> route.matches(request.getMethod(), path));
    }

    /**
     * Valor de {@code X-User-Name}, o {@code null} si no se envía. Netty escribe los headers en
     * ISO-8859-1 y Tomcat los lee igual, así que "José" o "Begoña" llegan intactos; un nombre con
     * otros caracteres (emoji, CJK) no se envía para no llegar corrupto: event-service lee el
     * claim firmado del token, que sí los conserva. Sin saltos de línea ni controles.
     */
    static String nameHeader(Object claim) {
        if (!(claim instanceof String raw)) {
            return null;
        }
        String name = raw.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH).strip();
        }
        if (name.isEmpty()) {
            return null;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean printableLatin1 = (c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF);
            if (!printableLatin1) {
                return null;
            }
        }
        return name;
    }

    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return -1; // Run before other filters
    }
}
