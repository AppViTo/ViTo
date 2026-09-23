package com.vito.auth.infrastructure.adapter.outbound.cache;

import com.vito.auth.domain.port.outbound.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String BLACKLIST_PREFIX = "blacklist:";
    
    @Override
    public void set(String key, String value, Duration expiration) {
        redisTemplate.opsForValue().set(key, value, expiration);
        log.debug("Cache SET: {} with expiration: {}", key, expiration);
    }
    
    @Override
    public Optional<String> get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        log.debug("Cache GET: {} = {}", key, value);
        return Optional.ofNullable(value);
    }
    
    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
        log.debug("Cache DELETE: {}", key);
    }
    
    @Override
    public boolean hasKey(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
    
    @Override
    public void addToBlacklist(String token, Duration expiration) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted", expiration);
        log.debug("Token added to blacklist: {}", token.substring(0, Math.min(20, token.length())) + "...");
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
