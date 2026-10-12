package com.vito.checkin.infrastructure.adapter.outbound.profile;

import com.vito.checkin.domain.model.CheckInEvent.UserProfile;
import com.vito.checkin.domain.port.outbound.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador provisional: checkin-service aún no tiene de dónde obtener el perfil del asistente.
 * Las cabeceras X-User-* del gateway (ADR-007) son las de quien escanea, no las del asistente,
 * y no incluyen nombre ni avatar. Las alternativas (llamar a social-service, añadir el perfil
 * al JWT/cabeceras o replicarlo desde user-events) requieren un RFC de contrato.
 *
 * Devuelve vacío: CheckInServiceImpl ya lo trata mostrando "Unknown User". La validación del
 * QR no depende del perfil, así que el check-in funciona igual.
 */
@Slf4j
@Component
public class UnresolvedUserProfileService implements UserProfileService {

    @Override
    public Optional<UserProfile> getUserProfile(String userId) {
        log.debug("User profile lookup not implemented yet; returning empty for user: {}", userId);
        return Optional.empty();
    }
}
