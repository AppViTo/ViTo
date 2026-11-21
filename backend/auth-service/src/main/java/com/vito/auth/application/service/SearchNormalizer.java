package com.vito.auth.application.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalización de la búsqueda de usuarios (Ola 2a, research R3): minúsculas, sin tildes (NFD sin
 * marcas diacríticas) y sin espacios de sobra. La usan el registro y la migración para llenar
 * {@code search.*}, y la búsqueda para el texto que escribe el usuario, así "jose" encuentra a
 * "José". No escapa nada: el texto queda literal y el adaptador lo escapa antes de consultar.
 */
public final class SearchNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchNormalizer() {
    }

    /** {@code search.username}: el nombre de usuario normalizado; null si no hay. */
    public static String username(String username) {
        return username == null ? null : normalize(username);
    }

    /** {@code search.terms}: las palabras del nombre visible, normalizadas, sin vacíos ni repetidas. */
    public static List<String> terms(String displayName) {
        if (displayName == null) {
            return List.of();
        }
        return Arrays.stream(SPACES.split(normalize(displayName)))
                .filter(term -> !term.isEmpty())
                .distinct()
                .toList();
    }

    /** Texto de búsqueda: sin una {@code @} inicial y normalizado. Vacío si es null. */
    public static String query(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.strip();
        if (text.startsWith("@")) {
            text = text.substring(1);
        }
        return normalize(text);
    }

    /** Longitud en caracteres (no en unidades UTF-16): un emoji cuenta como uno. */
    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String normalize(String text) {
        String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutAccents.toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
    }
}
