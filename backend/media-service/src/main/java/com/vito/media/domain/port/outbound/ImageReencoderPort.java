package com.vito.media.domain.port.outbound;

/**
 * Re-codifica una imagen subida (T-B25, excepción A3): decodifica los píxeles y escribe un
 * archivo nuevo, así que no sobreviven metadatos (EXIF, GPS) ni datos anexados (políglotas).
 */
public interface ImageReencoderPort {

    /** Tipos que se re-codifican; el resto (vídeo) no pasa por aquí. */
    boolean supports(String contentType);

    /**
     * @param contentType tipo real ya verificado por magic bytes (T-B19)
     * @throws com.vito.media.domain.exception.MediaException.UnreadableImageException si no decodifica
     * @throws com.vito.media.domain.exception.MediaException.ImageTooLargeException si supera el tope de píxeles
     */
    ReencodedImage reencode(byte[] data, String contentType);

    /** La única versión que se guarda: {@code contentType} puede cambiar (WebP se guarda como JPEG o PNG). */
    record ReencodedImage(byte[] data, String contentType, int width, int height) {
    }
}
