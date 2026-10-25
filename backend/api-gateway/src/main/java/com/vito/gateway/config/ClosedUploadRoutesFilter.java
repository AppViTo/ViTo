package com.vito.gateway.config;

import com.vito.gateway.error.ErrorResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Excepción A3 del triage de seguridad (T051, T-I17): en producción no se sube contenido hasta
 * que existan el antimalware y la moderación que exige la constitución. Responde {@code 404}
 * antes de Spring Security, del JWT y del enrutamiento, así que la petición no llega a los
 * servicios.
 *
 * <p>T-I21: con {@code vito.mosaic.uploads-enabled=true} (variable {@code MOSAIC_UPLOADS_ENABLED},
 * apagada por defecto) se abren la subida de media y las escrituras del mosaico que el
 * inventario publica en la Ola 3, incluida la moderación del organizador ({@code removeMosaicItem},
 * #182: sin ella la excepción A3 no se cumple). El resto de las escrituras sigue en {@code 404}: las rutas que
 * ADR-016 elimina ({@code /mosaic/photo}, {@code /mosaic/checkin}) y la interna
 * {@code /media/{id}/process}. Solo se activa cuando T-B25 y T-B26 estén desplegadas.
 */
@Component
@Profile("prod")
public class ClosedUploadRoutesFilter implements WebFilter, Ordered {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private static final List<Route> CLOSED_ROUTES = List.of(
            new Route(PARSER.parse("/api/v1/media/**"), Set.of(HttpMethod.POST)),
            new Route(PARSER.parse("/api/v1/mosaic/**"),
                    Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)));

    /** Excepciones a {@link #CLOSED_ROUTES} con la propiedad activa (Ola 3 de endpoint-inventory.md). */
    private static final List<Route> OPENED_WITH_UPLOADS = List.of(
            new Route(PARSER.parse("/api/v1/media/upload"), Set.of(HttpMethod.POST)),
            new Route(PARSER.parse("/api/v1/mosaic/post"), Set.of(HttpMethod.POST)),
            new Route(PARSER.parse("/api/v1/mosaic/grid/{eventId}/initialize"), Set.of(HttpMethod.POST)),
            new Route(PARSER.parse("/api/v1/mosaic/item/{itemId}/like"),
                    Set.of(HttpMethod.POST, HttpMethod.DELETE)),
            new Route(PARSER.parse("/api/v1/mosaic/item/{itemId}"), Set.of(HttpMethod.DELETE)));

    private final boolean uploadsEnabled;

    public ClosedUploadRoutesFilter(@Value("${vito.mosaic.uploads-enabled:false}") boolean uploadsEnabled) {
        this.uploadsEnabled = uploadsEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isClosed(exchange.getRequest())) {
            return ErrorResponses.write(exchange.getResponse(), HttpStatus.NOT_FOUND, ErrorResponses.NOT_FOUND,
                    "El recurso solicitado no existe");
        }
        return chain.filter(exchange);
    }

    private boolean isClosed(ServerHttpRequest request) {
        if (!matchesAny(CLOSED_ROUTES, request)) {
            return false;
        }
        return !(uploadsEnabled && matchesAny(OPENED_WITH_UPLOADS, request));
    }

    private static boolean matchesAny(List<Route> routes, ServerHttpRequest request) {
        return routes.stream().anyMatch(route -> route.methods().contains(request.getMethod())
                && route.path().matches(request.getPath().pathWithinApplication()));
    }

    /** Antes que el WebFilterChainProxy de Spring Security (-100). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private record Route(PathPattern path, Set<HttpMethod> methods) {
    }
}
