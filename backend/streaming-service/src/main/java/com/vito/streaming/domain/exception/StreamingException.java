package com.vito.streaming.domain.exception;

public class StreamingException extends RuntimeException {
    
    public StreamingException(String message) {
        super(message);
    }
    
    public static StreamingException streamNotFound() {
        return new StreamingException("Stream not found");
    }
    
    public static StreamingException notAuthorized() {
        return new StreamingException("You are not authorized to perform this action");
    }
    
    public static StreamingException streamAlreadyLive() {
        return new StreamingException("Stream is already live");
    }
    
    public static StreamingException streamNotLive() {
        return new StreamingException("Stream is not live");
    }
    
    public static StreamingException invalidStreamKey() {
        return new StreamingException("Invalid stream key");
    }
    
    public static StreamingException maxViewersReached() {
        return new StreamingException("Maximum viewers reached");
    }
    
    public static StreamingException chatDisabled() {
        return new StreamingException("Chat is disabled for this stream");
    }
    
    public static StreamingException reactionsDisabled() {
        return new StreamingException("Reactions are disabled for this stream");
    }
}
