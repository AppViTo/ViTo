package com.vito.gateway.security;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T067 (Principio VI, R4): el token de un enlace de invitación da acceso al evento, así que no
 * sale de la app. Las trazas OpenTelemetry del gateway (la del servidor y la del cliente HTTP
 * hacia event-service) y sus logs usan la plantilla {@code /api/v1/invite-links/{token}}.
 * Misma regla que en event-service.
 */
@Configuration
public class InviteLinkTokenRedaction {

    static final String TEMPLATE = "/api/v1/invite-links/{token}";

    /** Cualquier segmento tras {@code /api/v1/invite-links/}, sea o no un token válido. */
    private static final Pattern TOKEN_PATH = Pattern.compile("/api/v1/invite-links/[^/?#\\s\"']+");

    /** Copia del texto con la plantilla en lugar del token; {@code null} si es {@code null}. */
    public static String redact(String text) {
        if (text == null || !text.contains("/invite-links/")) {
            return text;
        }
        return TOKEN_PATH.matcher(text).replaceAll(Matcher.quoteReplacement(TEMPLATE));
    }

    /** Reescribe los atributos de cada observación antes de que se conviertan en atributos del span. */
    @Bean
    public ObservationFilter inviteLinkTokenObservationFilter() {
        return context -> {
            List<KeyValue> high = new ArrayList<>();
            context.getHighCardinalityKeyValues().forEach(high::add);
            for (KeyValue kv : high) {
                String safe = redact(kv.getValue());
                if (!safe.equals(kv.getValue())) {
                    context.addHighCardinalityKeyValue(KeyValue.of(kv.getKey(), safe));
                }
            }
            List<KeyValue> low = new ArrayList<>();
            context.getLowCardinalityKeyValues().forEach(low::add);
            for (KeyValue kv : low) {
                String safe = redact(kv.getValue());
                if (!safe.equals(kv.getValue())) {
                    context.addLowCardinalityKeyValue(KeyValue.of(kv.getKey(), safe));
                }
            }
            if (context.getContextualName() != null) {
                context.setContextualName(redact(context.getContextualName()));
            }
            return context;
        };
    }
}
