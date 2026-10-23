package com.vito.gateway.config;

import com.vito.gateway.error.ErrorResponses;
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
 * servicios. Se borra en la Ola 3 (mosaico), junto con el cierre de la excepción A3.
 */
@Component
@Profile("prod")
public class ClosedUploadRoutesFilter implements WebFilter, Ordered {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private static final List<ClosedRoute> CLOSED_ROUTES = List.of(
            new ClosedRoute(PARSER.parse("/api/v1/media/**"), Set.of(HttpMethod.POST)),
            new ClosedRoute(PARSER.parse("/api/v1/mosaic/**"),
                    Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isClosed(exchange.getRequest())) {
            return ErrorResponses.write(exchange.getResponse(), HttpStatus.NOT_FOUND, ErrorResponses.NOT_FOUND,
                    "El recurso solicitado no existe");
        }
        return chain.filter(exchange);
    }

    private static boolean isClosed(ServerHttpRequest request) {
        return CLOSED_ROUTES.stream().anyMatch(route -> route.methods().contains(request.getMethod())
                && route.path().matches(request.getPath().pathWithinApplication()));
    }

    /** Antes que el WebFilterChainProxy de Spring Security (-100). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private record ClosedRoute(PathPattern path, Set<HttpMethod> methods) {
    }
}
