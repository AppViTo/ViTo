package com.vito.media.domain.port.outbound;

import java.time.Duration;

public interface StoragePort {

    String uploadFile(String key, byte[] data, String contentType);
    byte[] downloadFile(String key);
    void deleteFile(String key);
    String generatePresignedUrl(String key, Duration ttl);
    String getPublicUrl(String key);
}
