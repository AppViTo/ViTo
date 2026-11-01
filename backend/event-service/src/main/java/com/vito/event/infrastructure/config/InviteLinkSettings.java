package com.vito.event.infrastructure.config;

import java.net.URI;

/**
 * Ola 1 (R4): base de las URLs de los enlaces de invitación, {@code <baseUrl>/i/<token>}.
 * Es la web de la app (Cloudflare Pages en producción), no el gateway.
 *
 * @param baseUrl URL absoluta http(s), sin barra final
 */
public record InviteLinkSettings(String baseUrl) {

    public InviteLinkSettings {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("INVITE_LINK_BASE_URL no está definida");
        }
        baseUrl = baseUrl.strip();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("INVITE_LINK_BASE_URL no es una URL válida", e);
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) {
            throw new IllegalStateException("INVITE_LINK_BASE_URL debe ser una URL http(s) absoluta");
        }
    }

    /** URL pública de un enlace; el token no se registra en logs (R4). */
    public String linkUrl(String token) {
        return baseUrl + "/i/" + token;
    }
}
