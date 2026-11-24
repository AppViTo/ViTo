package com.vito.social.domain.port.outbound;

import com.vito.social.domain.model.UserSummary;

import java.util.List;

/**
 * Identidad pública de los usuarios (FR-012). La implementa un adaptador que llama a
 * {@code getUsersByIds} de auth-service: social-service guarda solo el grafo (Principio II).
 */
public interface UserDirectory {

    /**
     * Datos públicos en el orden pedido, sin los que ya no existen y sin repetir. Sin ids no
     * consulta nada.
     *
     * @throws com.vito.social.domain.exception.UserDirectoryUnavailableException si auth-service
     *         no responde a tiempo o falla
     * @throws com.vito.social.domain.exception.SocialException con {@code UNAUTHORIZED} si
     *         auth-service rechaza la sesión
     */
    List<UserSummary> findByIds(List<String> ids);
}
