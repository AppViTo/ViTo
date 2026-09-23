package com.vito.chat.domain.exception;

public class ChatException extends RuntimeException {

    public ChatException(String message) {
        super(message);
    }

    public ChatException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class RoomNotFoundException extends ChatException {
        public RoomNotFoundException(String roomId) {
            super("Chat room not found: " + roomId);
        }
    }

    public static class MessageNotFoundException extends ChatException {
        public MessageNotFoundException(String messageId) {
            super("Message not found: " + messageId);
        }
    }

    public static class UnauthorizedException extends ChatException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static class RoomFullException extends ChatException {
        public RoomFullException(String roomId) {
            super("Chat room is full: " + roomId);
        }
    }
}
