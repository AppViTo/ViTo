package com.vito.media.domain.port.outbound;

import com.vito.media.domain.model.Media;

import java.util.List;
import java.util.Optional;

public interface StoragePort {

    String uploadFile(String key, byte[] data, String contentType);
    byte[] downloadFile(String key);
    void deleteFile(String key);
    String generatePresignedUrl(String key, long expirationMinutes);
    String getPublicUrl(String key);
}
