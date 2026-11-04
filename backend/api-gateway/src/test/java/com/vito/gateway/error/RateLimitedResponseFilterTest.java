package com.vito.gateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitedResponseFilterTest {

    private final RateLimitedResponseFilter filter = new RateLimitedResponseFilter();

    @Test
    void emptyRateLimiterResponseBecomesRateLimitedEnvelope() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/events"));
        // Lo mismo que hace RequestRateLimiterGatewayFilterFactory al denegar
        GatewayFilterChain rateLimiter = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, rateLimiter).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        ErrorResponseContract.assertMatches(exchange.getResponse().getBodyAsString().block(), 429, "RATE_LIMITED");
        assertEquals("1", exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void serviceResponsesWithBodyAreNotTouched() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/events/evt_1"));
        String serviceBody = "{\"error\":\"RATE_LIMITED\",\"message\":\"del servicio\"}";
        GatewayFilterChain service = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().writeWith(Mono.just(ex.getResponse().bufferFactory()
                    .wrap(serviceBody.getBytes(StandardCharsets.UTF_8))));
        };

        filter.filter(exchange, service).block();

        assertEquals(serviceBody, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    void otherEmptyResponsesAreNotTouched() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.delete("/api/v1/x"));
        GatewayFilterChain noContent = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, noContent).block();

        assertEquals(HttpStatus.NO_CONTENT, exchange.getResponse().getStatusCode());
        assertEquals("", exchange.getResponse().getBodyAsString().defaultIfEmpty("").block());
    }

    /** CONVENTIONS §9: 429 con Retry-After calculado del limitador (T019). */
    @Test
    void retryAfterComesFromTheLimiterHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/invite-links/x"));
        GatewayFilterChain previewLimiter = ex -> {
            ex.getResponse().getHeaders().set(RateLimitedResponseFilter.REQUESTED_TOKENS, "6");
            ex.getResponse().getHeaders().set(RateLimitedResponseFilter.REPLENISH_RATE, "1");
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, previewLimiter).block();

        assertEquals("6", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        assertEquals(true, exchange.getResponse().getBodyAsString().block()
                .contains("Has superado el límite de peticiones. Reintenta en 6 segundos"));
    }

    @Test
    void retryAfterIsOneSecondByDefault() {
        HttpHeaders none = new HttpHeaders();
        assertEquals(1, RateLimitedResponseFilter.retryAfterSeconds(none));
        HttpHeaders events = new HttpHeaders();
        events.set(RateLimitedResponseFilter.REQUESTED_TOKENS, "1");
        events.set(RateLimitedResponseFilter.REPLENISH_RATE, "20");
        assertEquals(1, RateLimitedResponseFilter.retryAfterSeconds(events));
        assertEquals("Has superado el límite de peticiones. Reintenta en 1 segundo", RateLimitedResponseFilter.message(1));
    }
}
