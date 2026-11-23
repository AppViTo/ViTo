package com.vito.auth.domain.port.inbound;

import com.vito.auth.domain.model.UserSummary;

import java.util.List;

/**
 * Directorio público de usuarios (Ola 2a, ADR-016: la identidad es de auth-service). Lo usan la
 * app y {@code social-service} para completar nombres y avatares.
 */
public interface UserDirectoryService {

    int MAX_IDS = 50;
    int MIN_QUERY_LENGTH = 2;
    int MAX_QUERY_LENGTH = 50;
    int DEFAULT_SEARCH_SIZE = 20;
    int MAX_SEARCH_SIZE = 20;

    /**
     * {@code searchUsers}: usuarios por prefijo de username o de una palabra del nombre visible,
     * sin tildes ni mayúsculas; nunca por correo, sin {@code requesterId} y sin perfiles privados
     * (FR-001 a FR-005).
     *
     * @param query       texto tal cual lo escribió el usuario; una @ inicial se ignora
     * @param requesterId quien busca (de la sesión); no aparece en sus resultados
     * @param page        base 0; null = 0
     * @param size        de 1 a 20; null = 20
     * @throws com.vito.auth.domain.exception.InvalidRequestException si {@code query} no tiene de 2
     *         a 50 caracteres tras quitar la @, o la página no es válida
     */
    List<UserSummary> search(String query, String requesterId, Integer page, Integer size);

    /**
     * {@code getUsersByIds}: datos públicos en el orden pedido, sin los que no existen y sin
     * repetir. Incluye perfiles privados (la privacidad solo aplica a la búsqueda, FR-004).
     *
     * @param ids entre 1 y {@link #MAX_IDS}, contando los repetidos
     * @throws com.vito.auth.domain.exception.InvalidRequestException si no hay ids o hay más de 50
     */
    List<UserSummary> findByIds(List<String> ids);
}
