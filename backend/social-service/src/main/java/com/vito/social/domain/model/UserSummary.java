package com.vito.social.domain.model;

/**
 * Datos públicos de un usuario (schema {@code UserSummary} de openapi.yaml, Ola 2a). Su dueño es
 * auth-service (ADR-016): social-service no los guarda, los pide en cada respuesta (FR-012).
 * {@code displayName} y {@code avatar} pueden ser null. Nunca correo ni teléfono.
 */
public record UserSummary(String userId, String username, String displayName, String avatar) {
}
