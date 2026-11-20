package com.vito.auth.domain.port.inbound;

import com.vito.auth.domain.model.UserSummary;

import java.util.List;

/**
 * Directorio público de usuarios (Ola 2a, ADR-016: la identidad es de auth-service). Lo usan la
 * app y {@code social-service} para completar nombres y avatares.
 */
public interface UserDirectoryService {

    int MAX_IDS = 50;

    /**
     * {@code getUsersByIds}: datos públicos en el orden pedido, sin los que no existen y sin
     * repetir. Incluye perfiles privados (la privacidad solo aplica a la búsqueda, FR-004).
     *
     * @param ids entre 1 y {@link #MAX_IDS}, contando los repetidos
     * @throws com.vito.auth.domain.exception.InvalidRequestException si no hay ids o hay más de 50
     */
    List<UserSummary> findByIds(List<String> ids);
}
