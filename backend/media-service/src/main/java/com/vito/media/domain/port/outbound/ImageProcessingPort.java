package com.vito.media.domain.port.outbound;

import com.vito.media.domain.model.Media;

import java.util.List;
import java.util.Optional;

public interface ImageProcessingPort {

    Media.MediaMetadata extractMetadata(byte[] data, String contentType);
    byte[] createThumbnail(byte[] data, int width, int height);
    byte[] resizeImage(byte[] data, int maxWidth, int maxHeight);
    boolean isImage(String contentType);
    boolean isVideo(String contentType);
}
