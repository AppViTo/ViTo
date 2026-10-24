package com.vito.media.domain.exception;

import java.util.List;

public class MediaException extends RuntimeException {

    public MediaException(String message) {
        super(message);
    }

    public MediaException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class MediaNotFoundException extends MediaException {
        public MediaNotFoundException(String mediaId) {
            super("Media not found: " + mediaId);
        }
    }

    public static class UploadFailedException extends MediaException {
        public UploadFailedException(String message) {
            super("Upload failed: " + message);
        }
    }

    public static class ProcessingFailedException extends MediaException {
        public ProcessingFailedException(String message) {
            super("Processing failed: " + message);
        }
    }

    public static class UnauthorizedException extends MediaException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    /** Archivo rechazado por validación (400 VALIDATION_ERROR); {@code detail} va en {@code details.file}. */
    public static class InvalidFileException extends MediaException {
        private final String detail;

        public InvalidFileException(String message, String detail) {
            super(message);
            this.detail = detail;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static class EmptyFileException extends InvalidFileException {
        public EmptyFileException() {
            super("El archivo está vacío", "Adjunta un archivo con contenido");
        }
    }

    public static class FileTooLargeException extends InvalidFileException {
        public FileTooLargeException(long maxSize) {
            super("El archivo supera el tamaño máximo de " + toMegabytes(maxSize) + " MB",
                    "Tamaño máximo: " + toMegabytes(maxSize) + " MB");
        }

        private static long toMegabytes(long bytes) {
            return bytes / (1024 * 1024);
        }
    }

    public static class UnsupportedFileTypeException extends InvalidFileException {
        public UnsupportedFileTypeException(String contentType, List<String> allowedTypes) {
            super("El tipo de archivo " + (contentType != null ? contentType : "(sin tipo)") + " no está permitido",
                    allowedTypesDetail(allowedTypes));
        }
    }

    /** El contenido real (magic bytes) no coincide con el Content-Type declarado. */
    public static class ContentTypeMismatchException extends InvalidFileException {
        public ContentTypeMismatchException(String declaredType, String detectedType, List<String> allowedTypes) {
            super("El contenido del archivo es " + detectedType + ", no " + declaredType,
                    allowedTypesDetail(allowedTypes));
        }
    }

    /**
     * La excepción A3 solo publica imágenes re-codificadas: el vídeo se rechaza en esta ola aunque
     * CONVENTIONS §8 aún lo liste (decisión de los leads, 2026-10-24).
     */
    public static class VideoNotAllowedException extends InvalidFileException {
        public VideoNotAllowedException() {
            super("Por ahora no se admiten vídeos: sube una imagen JPEG, PNG o WebP",
                    "Formatos permitidos por ahora: image/jpeg, image/png, image/webp");
        }
    }

    /** El tipo es de imagen pero el contenido no decodifica (dañado o falsificado) (T-B25). */
    public static class UnreadableImageException extends InvalidFileException {
        public UnreadableImageException() {
            super("La imagen está dañada o no se puede leer", "Sube una imagen JPEG, PNG o WebP válida");
        }
    }

    /** Más píxeles que el tope: frena las bombas de descompresión antes de reservar memoria (T-B25). */
    public static class ImageTooLargeException extends InvalidFileException {
        public ImageTooLargeException(long maxPixels) {
            super("La imagen supera el máximo de " + maxPixels / 1_000_000 + " megapíxeles",
                    "Resolución máxima: " + maxPixels / 1_000_000 + " megapíxeles");
        }
    }

    private static String allowedTypesDetail(List<String> allowedTypes) {
        return "Formatos permitidos: " + String.join(", ", allowedTypes);
    }
}
