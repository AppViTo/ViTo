package com.vito.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * T-B37 (punto 4): abre la conexión compartida de Lettuce al arrancar.
 *
 * <p>Spring Data Redis la crea en la primera operación y ese primer {@code connect} es
 * bloqueante. Sin esto lo pagaba la primera petición con token (consulta de revocación) o la
 * primera de una ruta con {@code RequestRateLimiter}, en un hilo de Netty: con una ráfaga al
 * arrancar, todas las peticiones de ese hilo esperaban detrás (k6, T061: 121 consultas de
 * revocación por encima de 250 ms con Redis en reposo).
 *
 * <p>Si Redis no responde, solo avisa: el gateway arranca igual y degrada como ya hacía
 * (revocación y rate limit fallan abiertos).
 */
@Slf4j
@Component
public class RedisConnectionWarmUp implements ApplicationRunner {

    private final ReactiveStringRedisTemplate redis;
    private final Duration timeout;

    public RedisConnectionWarmUp(ReactiveStringRedisTemplate redis,
                                 @Value("${vito.gateway.redis-warm-up-timeout:5s}") Duration timeout) {
        this.redis = redis;
        this.timeout = timeout;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            redis.hasKey("vito:gateway:warm-up").block(timeout);
            log.info("Redis connection ready");
        } catch (RuntimeException e) {
            log.warn("Redis not reachable at startup, the connection will open on first use: {}", e.toString());
        }
    }
}
