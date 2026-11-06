package com.vito.event.infrastructure.adapter.inbound.rest;

/**
 * {@code SendInvitationRequest} del contrato. La validación (obligatorio, 280 caracteres) está
 * en el servicio, para que el 400 tenga el texto de los fixtures.
 */
public record SendInvitationRequest(String inviteeId, String message) {
}
