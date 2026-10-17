package com.vito.event.infrastructure.adapter.inbound.rest.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vito.event.domain.model.Event;
import org.springframework.boot.jackson.JsonMixin;

/**
 * Nombre JSON de ADR-001 para {@code settings.isPublic}. Lombok genera {@code isPublic()} /
 * {@code setPublic()} para un {@code boolean isPublic}, y Jackson lo expondría como
 * {@code "public"}. El mixin lo corrige sin acoplar el dominio a Jackson.
 */
@JsonMixin(Event.EventSettings.class)
abstract class EventSettingsJsonMixin {

    @JsonProperty("isPublic")
    abstract boolean isPublic();

    @JsonProperty("isPublic")
    abstract void setPublic(boolean isPublic);
}
