package com.vito.media.domain.exception;

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

    public static class FileTooLargeException extends MediaException {
        public FileTooLargeException(long maxSize) {
            super("File size exceeds maximum limit: " + maxSize + " bytes");
        }
    }

    public static class UnsupportedFileTypeException extends MediaException {
        public UnsupportedFileTypeException(String contentType) {
            super("Unsupported file type: " + contentType);
        }
    }
}
