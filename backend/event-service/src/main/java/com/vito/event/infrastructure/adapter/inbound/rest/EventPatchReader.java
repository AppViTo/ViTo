package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.model.EventPatch.Change;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee el cuerpo {@code application/merge-patch+json} de {@code updateEvent} (US2) y lo convierte
 * en {@link EventPatch}. Aquí se resuelve la forma del JSON (campos no editables, desconocidos y
 * tipos); las reglas de negocio (longitudes, fechas, cupo) las aplica {@code EventServiceImpl}.
 */
final class EventPatchReader {

    /** Campos del recurso Event que existen pero no se editan (fixture updateEvent.400). */
    static final Set<String> NOT_EDITABLE = Set.of("id", "organizerId", "organizerDisplayName", "status", "stats",
            "createdAt", "updatedAt");

    private static final Set<String> ROOT = Set.of("title", "description", "details", "settings");
    private static final Set<String> DETAILS = Set.of("startDate", "endDate", "location", "address", "latitude",
            "longitude", "coverImages", "tags", "category");
    private static final Set<String> SETTINGS = Set.of("isPublic", "requiresApproval", "maxAttendees",
            "allowGuestInvites", "enableLiveStreaming", "enableMosaic", "hideAddressUntilAccepted");

    private EventPatchReader() {
    }

    static EventPatch read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw EventException.invalidPatchBody();
        }
        EventPatch.EventPatchBuilder patch = EventPatch.builder();
        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (NOT_EDITABLE.contains(field)) {
                throw EventException.notEditableField(field);
            }
            if (!ROOT.contains(field)) {
                throw EventException.unknownField(field);
            }
            switch (field) {
                case "title" -> patch.title(Change.of(text(field, value)));
                case "description" -> patch.description(Change.of(text(field, value)));
                case "details" -> readDetails(object(field, value), patch);
                case "settings" -> readSettings(object(field, value), patch);
                default -> throw new IllegalStateException(field);
            }
        }
        EventPatch result = patch.build();
        if (result.isEmpty()) {
            throw EventException.emptyPatch();
        }
        return result;
    }

    private static void readDetails(JsonNode details, EventPatch.EventPatchBuilder patch) {
        for (Map.Entry<String, JsonNode> entry : details.properties()) {
            String name = entry.getKey();
            String field = "details." + name;
            JsonNode value = entry.getValue();
            if (!DETAILS.contains(name)) {
                throw EventException.unknownField(field);
            }
            switch (name) {
                case "startDate" -> patch.startDate(Change.of(instant(field, value)));
                case "endDate" -> patch.endDate(Change.of(instant(field, value)));
                case "location" -> patch.location(Change.of(text(field, value)));
                case "address" -> patch.address(Change.of(text(field, value)));
                case "latitude" -> patch.latitude(Change.of(number(field, value)));
                case "longitude" -> patch.longitude(Change.of(number(field, value)));
                case "coverImages" -> patch.coverImages(Change.of(texts(field, value)));
                case "tags" -> patch.tags(Change.of(texts(field, value)));
                case "category" -> patch.category(Change.of(text(field, value)));
                default -> throw new IllegalStateException(name);
            }
        }
    }

    private static void readSettings(JsonNode settings, EventPatch.EventPatchBuilder patch) {
        for (Map.Entry<String, JsonNode> entry : settings.properties()) {
            String name = entry.getKey();
            String field = "settings." + name;
            JsonNode value = entry.getValue();
            if (!SETTINGS.contains(name)) {
                throw EventException.unknownField(field);
            }
            switch (name) {
                case "isPublic" -> patch.isPublic(Change.of(bool(field, value)));
                case "requiresApproval" -> patch.requiresApproval(Change.of(bool(field, value)));
                case "maxAttendees" -> patch.maxAttendees(Change.of(integer(field, value)));
                case "allowGuestInvites" -> patch.allowGuestInvites(Change.of(bool(field, value)));
                case "enableLiveStreaming" -> patch.enableLiveStreaming(Change.of(bool(field, value)));
                case "enableMosaic" -> patch.enableMosaic(Change.of(bool(field, value)));
                case "hideAddressUntilAccepted" -> patch.hideAddressUntilAccepted(Change.of(bool(field, value)));
                default -> throw new IllegalStateException(name);
            }
        }
    }

    /** {@code details} y {@code settings} se editan por campos; {@code null} los borraría enteros. */
    private static JsonNode object(String field, JsonNode value) {
        if (!value.isObject()) {
            throw EventException.invalidField(field, "Debe ser un objeto");
        }
        return value;
    }

    private static String text(String field, JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw EventException.invalidField(field, "Debe ser texto");
        }
        return value.stringValue();
    }

    private static Instant instant(String field, JsonNode value) {
        String text = text(field, value);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw EventException.invalidField(field, "Debe ser una fecha ISO-8601 en UTC");
        }
    }

    private static Double number(String field, JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw EventException.invalidField(field, "Debe ser un número");
        }
        return value.doubleValue();
    }

    private static Integer integer(String field, JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw EventException.invalidField(field, "Debe ser un número entero");
        }
        return value.intValue();
    }

    private static Boolean bool(String field, JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw EventException.invalidField(field, "Debe ser true o false");
        }
        return value.booleanValue();
    }

    private static List<String> texts(String field, JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw EventException.invalidField(field, "Debe ser una lista de textos");
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                throw EventException.invalidField(field, "Debe ser una lista de textos");
            }
            items.add(item.stringValue());
        }
        return List.copyOf(items);
    }
}
