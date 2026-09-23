package com.vito.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth Service
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/auth/(?<segment>.*)", "/api/v1/auth/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("authServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/auth"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setStatuses(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("http://localhost:8081"))

                // Social Service
                .route("social-service", r -> r
                        .path("/api/v1/social/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/social/(?<segment>.*)", "/api/v1/social/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("socialServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/social"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setStatuses(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("http://localhost:8082"))

                // Event Service
                .route("event-service", r -> r
                        .path("/api/v1/events/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/events/(?<segment>.*)", "/api/v1/events/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("eventServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/events"))
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(userKeyResolver())))
                        .uri("http://localhost:8083"))

                // Check-In Service
                .route("checkin-service", r -> r
                        .path("/api/v1/checkin/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/checkin/(?<segment>.*)", "/api/v1/checkin/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("checkinServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/checkin")))
                        .uri("http://localhost:8084"))

                // Mosaic Service
                .route("mosaic-service", r -> r
                        .path("/api/v1/mosaic/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/mosaic/(?<segment>.*)", "/api/v1/mosaic/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("mosaicServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/mosaic")))
                        .uri("http://localhost:8085"))

                // Streaming Service
                .route("streaming-service", r -> r
                        .path("/api/v1/streaming/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/streaming/(?<segment>.*)", "/api/v1/streaming/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("streamingServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/streaming")))
                        .uri("http://localhost:8086"))

                // Archive Service
                .route("archive-service", r -> r
                        .path("/api/v1/archive/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/archive/(?<segment>.*)", "/api/v1/archive/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("archiveServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/archive")))
                        .uri("http://localhost:8087"))

                // Chat Service
                .route("chat-service", r -> r
                        .path("/api/v1/chat/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/chat/(?<segment>.*)", "/api/v1/chat/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("chatServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/chat")))
                        .uri("http://localhost:8088"))

                // Media Service
                .route("media-service", r -> r
                        .path("/api/v1/media/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/media/(?<segment>.*)", "/api/v1/media/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("mediaServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/media")))
                        .uri("http://localhost:8089"))

                // Transcode Service (Python AI)
                .route("transcode-service", r -> r
                        .path("/api/v1/transcode/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/transcode/(?<segment>.*)", "/api/v1/transcode/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("transcodeServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/transcode")))
                        .uri("http://localhost:8000"))

                // Recommendation Service (Python AI)
                .route("recommendation-service", r -> r
                        .path("/api/v1/recommendations/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/recommendations/(?<segment>.*)", "/api/v1/recommendations/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("recommendationServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/recommendations")))
                        .uri("http://localhost:8001"))

                // Moderation Service (Python AI)
                .route("moderation-service", r -> r
                        .path("/api/v1/moderation/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/moderation/(?<segment>.*)", "/api/v1/moderation/${segment}")
                                .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                                .circuitBreaker(config -> config
                                        .setName("moderationServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/moderation")))
                        .uri("http://localhost:8002"))

                .build();
    }
}
