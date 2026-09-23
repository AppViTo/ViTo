package com.vito.social.domain.exception;

public class SocialException extends RuntimeException {
    
    public SocialException(String message) {
        super(message);
    }
    
    public static SocialException cannotFollowYourself() {
        return new SocialException("You cannot follow yourself");
    }
    
    public static SocialException alreadyFollowing() {
        return new SocialException("You are already following this user");
    }
    
    public static SocialException notFollowing() {
        return new SocialException("You are not following this user");
    }
    
    public static SocialException userNotFound() {
        return new SocialException("User not found");
    }
    
    public static SocialException cannotFollowPrivateUser() {
        return new SocialException("Cannot follow private user without approval");
    }
}
