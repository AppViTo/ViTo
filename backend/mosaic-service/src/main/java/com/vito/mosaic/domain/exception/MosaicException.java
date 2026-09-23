package com.vito.mosaic.domain.exception;

public class MosaicException extends RuntimeException {
    
    public MosaicException(String message) {
        super(message);
    }
    
    public static MosaicException eventNotFound() {
        return new MosaicException("Event not found");
    }
    
    public static MosaicException itemNotFound() {
        return new MosaicException("Mosaic item not found");
    }
    
    public static MosaicException notAuthorized() {
        return new MosaicException("You are not authorized to perform this action");
    }
    
    public static MosaicException gridNotInitialized() {
        return new MosaicException("Mosaic grid is not initialized");
    }
    
    public static MosaicException gridFull() {
        return new MosaicException("Mosaic grid is full");
    }
}
