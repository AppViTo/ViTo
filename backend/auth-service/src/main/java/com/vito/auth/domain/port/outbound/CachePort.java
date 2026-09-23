package com.vito.auth.domain.port.outbound;

import java.time.Duration;
import java.util.Optional;

public interface CachePort {
    
    void set(String key, String value, Duration expiration);
    
    Optional<String> get(String key);
    
    void delete(String key);
    
    boolean hasKey(String key);
    
    void addToBlacklist(String token, Duration expiration);
    
    boolean isBlacklisted(String token);
}
