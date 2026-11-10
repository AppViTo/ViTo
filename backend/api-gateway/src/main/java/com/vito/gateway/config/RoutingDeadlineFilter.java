package com.vito.gateway.config;

import com.vito.gateway.error.ErrorResponses;
import com.vito.gateway.security.InviteLinkTokenRedaction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * T-B37 (punto 4): plazo para la fase del gateway <b>anterior</b> al envío al servicio.
 *
 * <p>En k6 (T061) un RSVP quedó colgado 60 s sin llegar al filtro CircuitBreaker: lo que corre
 * antes (Spring Security, {@code JwtAuthenticationFilter} con la consulta de revocación en Redis,
 * el {@code RequestRateLimiter}) no tenía un tope común. El TimeLimiter del breaker y el
 * {@code response-timeout} del cliente HTTP solo cubren la llamada al servicio.
 *
 * <p>El plazo corre desde que entra la petición hasta que el filtro de enrutado (Netty) la toma;
 * a partir de ahí mandan esos dos timeouts, así que no corta subidas lentas ni respuestas largas.
 * Si vence, la petición recibe {@code 503 SERVICE_UNAVAILABLE} con el envelope de ADR-003 y se
 * cuenta en {@code vito.gateway.routing.deadline.exceeded}. No aplica a los WebSocket, cuyo
 * intercambio dura lo que la sesión.
 */
@Slf4j
@Component
public class RoutingDeadlineFilter implements WebFilter, Ordered {

    /** Atributo del exchange: la petición ya llegó al filtro de enrutado. */
    static final String ROUTED_ATTR = "vito.routingStarted";

    static final String METRIC = "vito.gateway.routing.deadline.exceeded";

    static final String MESSAGE = "El servicio no está disponible en este momento. Inténtalo de nuevo en unos segundos";

    private final Duration deadline;
    private final Counter exceeded;

    public RoutingDeadlineFilter(@Value("${vito.gateway.routing-deadline:3s}") Duration deadline,
                                 MeterRegistry meterRegistry) {
        this.deadline = deadline;
        this.exceeded = Counter.builder(METRIC)
                .description("Peticiones que no llegaron al enrutado dentro del plazo del gateway")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if ("websocket".equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade())) {
            return chain.filter(exchange);
        }
        AtomicBoolean routed = new AtomicBoolean(false);
        exchange.getAttributes().put(ROUTED_ATTR, routed);

        // Solo emite (error) si al vencer la petición no se enrutó ni tiene respuesta en curso;
        // si no, nunca emite y gana la cadena.
        Mono<Void> timer = Mono.delay(deadline).flatMap(tick ->
                routed.get() || exchange.getResponse().isCommitted()
                        ? Mono.never()
                        : Mono.error(new DeadlineExceeded()));

        return Mono.firstWithSignal(chain.filter(exchange), timer)
                .onErrorResume(DeadlineExceeded.class, e -> {
                    exceeded.increment();
                    log.warn("Routing deadline of {} exceeded for {} {}", deadline,
                            exchange.getRequest().getMethod(),
                            InviteLinkTokenRedaction.redact(exchange.getRequest().getURI().getPath()));
                    return ErrorResponses.write(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE,
                            ErrorResponses.SERVICE_UNAVAILABLE, MESSAGE);
                });
    }

    /** Justo detrás de {@link ClosedUploadRoutesFilter} y antes de Spring Security (-100). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private static final class DeadlineExceeded extends RuntimeException {
        DeadlineExceeded() {
            super(null, null, false, false);
        }
    }

    /**
     * Marca que la petición llegó al enrutado. Corre después de todos los filtros de la ruta
     * (CircuitBreaker, RequestRateLimiter…) y antes de {@code WebsocketRoutingFilter}
     * ({@code LOWEST_PRECEDENCE - 1}) y {@code NettyRoutingFilter} ({@code LOWEST_PRECEDENCE}).
     */
    @Component
    static class RoutingStartedMarker implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            AtomicBoolean routed = exchange.getAttribute(ROUTED_ATTR);
            if (routed != null) {
                routed.set(true);
            }
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE - 10;
        }
    }
}
