package com.vito.social.infrastructure.adapter.outbound.directory;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.exception.UserDirectoryUnavailableException;
import com.vito.social.domain.model.UserSummary;
import com.vito.social.domain.port.outbound.UserDirectory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * T018 (Ola 2a, FR-012, research R1 y R2): pide a auth-service los datos públicos de una página
 * de usuarios con {@code GET /api/v1/users?ids=} ({@code getUsersByIds}), directo por el DNS de
 * Compose ({@code AUTH_SERVICE_URI}) y con el mismo Bearer de la petición en curso, como
 * {@code EventServiceOrganizerAdapter} de mosaic-service.
 *
 * <p>Timeout de conexión de 1 s y de lectura de 2 s. No reintenta dentro de la petición: si
 * auth-service falla, {@link UserDirectoryUnavailableException} (503) y el usuario reintenta.
 */
@Slf4j
@Component
public class AuthUserDirectoryAdapter implements UserDirectory {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(2);
    /** Máximo de ids de {@code getUsersByIds} (openapi.yaml). */
    static final int MAX_IDS_PER_CALL = 50;

    private final RestClient restClient;

    @Autowired
    public AuthUserDirectoryAdapter(RestClient.Builder builder, @Value("${auth-service.uri}") String authServiceUri) {
        this(builder.clone()
                .requestFactory(requestFactory())
                .baseUrl(authServiceUri)
                .build());
    }

    /** Para pruebas: un {@link RestClient} ya armado (por ejemplo, con MockRestServiceServer). */
    AuthUserDirectoryAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public List<UserSummary> findByIds(List<String> ids) {
        List<String> unique = List.copyOf(new LinkedHashSet<>(ids));
        if (unique.isEmpty()) {
            return List.of();
        }

        Map<String, UserSummary> found = new LinkedHashMap<>();
        for (int from = 0; from < unique.size(); from += MAX_IDS_PER_CALL) {
            List<String> batch = unique.subList(from, Math.min(from + MAX_IDS_PER_CALL, unique.size()));
            for (UserSummaryResponse user : fetch(batch)) {
                if (user != null && user.userId() != null) {
                    found.putIfAbsent(user.userId(), user.toDomain());
                }
            }
        }
        return unique.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private List<UserSummaryResponse> fetch(List<String> ids) {
        try {
            UserSummaryResponse[] body = restClient.get()
                    .uri(uri -> uri.path("/api/v1/users").queryParam("ids", String.join(",", ids)).build())
                    .headers(headers -> currentAuthorization().ifPresent(
                            value -> headers.set(HttpHeaders.AUTHORIZATION, value)))
                    .retrieve()
                    .body(UserSummaryResponse[].class);
            return body == null ? List.of() : new ArrayList<>(List.of(body));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw SocialException.sessionExpired();
            }
            // Principio VI: sin ids de personas en el log
            log.error("auth-service answered {} to getUsersByIds ({} ids)", e.getStatusCode().value(), ids.size());
            throw new UserDirectoryUnavailableException(e);
        } catch (RestClientException e) {
            // Timeout, conexión rechazada o cuerpo ilegible
            log.error("getUsersByIds failed: {} ({} ids)", e.getClass().getSimpleName(), ids.size());
            throw new UserDirectoryUnavailableException(e);
        }
    }

    private static Optional<String> currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
        }
        return Optional.empty();
    }

    /** Respuesta de {@code getUsersByIds}: el schema {@code UserSummary}. */
    record UserSummaryResponse(String userId, String username, String displayName, String avatar) {
        UserSummary toDomain() {
            return new UserSummary(userId, username, displayName, avatar);
        }
    }
}
