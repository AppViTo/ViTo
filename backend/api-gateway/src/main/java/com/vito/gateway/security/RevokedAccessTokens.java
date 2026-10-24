package com.vito.gateway.security;

import reactor.core.publisher.Mono;

/**
 * Lista de access tokens revocados por {@code jti} (T-B21). auth-service la escribe al hacer
 * logout; el gateway la consulta después de verificar la firma.
 */
@FunctionalInterface
public interface RevokedAccessTokens {

    /** {@code true} si el token con este {@code jti} fue revocado. Nunca emite error. */
    Mono<Boolean> isRevoked(String jti);
}
