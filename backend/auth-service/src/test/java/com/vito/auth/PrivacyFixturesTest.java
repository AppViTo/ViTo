package com.vito.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T055 (Ola 2a, SC-006): ningún fixture de {@code users/} ni de {@code social/} expone el correo o
 * el teléfono de una persona. Los fixtures son el ejemplo que copian el backend, la app y el mock:
 * si un dato privado entra aquí, termina en una respuesta.
 */
class PrivacyFixturesTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures");
    private static final Pattern PRIVATE_KEY = Pattern.compile("\"(email|phone|phoneNumber)\"\\s*:");
    private static final Pattern EMAIL_LIKE = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");

    @Test
    void usersAndSocialFixturesHaveNoEmailOrPhone() throws IOException {
        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (String folder : List.of("users", "social")) {
            try (Stream<Path> files = Files.list(FIXTURES.resolve(folder))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                    String json = Files.readString(file);
                    checked++;
                    if (PRIVATE_KEY.matcher(json).find()) {
                        leaks.add(folder + "/" + file.getFileName() + ": campo email o phone");
                    }
                    if (EMAIL_LIKE.matcher(json).find()) {
                        leaks.add(folder + "/" + file.getFileName() + ": texto con forma de correo");
                    }
                }
            }
        }

        assertTrue(checked > 0, "no se encontró ningún fixture en " + FIXTURES.toAbsolutePath());
        assertTrue(leaks.isEmpty(), "Datos privados en fixtures: " + leaks);
    }

    /** La prueba de arriba detecta de verdad un correo (no pasa por estar mal escrita). */
    @Test
    void detectorRecognisesEmailsAndPrivateKeys() {
        assertTrue(EMAIL_LIKE.matcher("{\"username\":\"ana@vito.com\"}").find());
        assertTrue(PRIVATE_KEY.matcher("{\"email\": \"x\"}").find());
        assertFalse(EMAIL_LIKE.matcher("{\"avatar\":\"https://cdn.vito.com/usr_1/avatar.jpg\"}").find());
    }
}
