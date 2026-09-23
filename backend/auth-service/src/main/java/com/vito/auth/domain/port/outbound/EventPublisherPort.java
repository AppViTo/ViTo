package com.vito.auth.domain.port.outbound;

public interface EventPublisherPort {
    
    void publish(String topic, String key, Object payload);
}
