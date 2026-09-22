package com.food.delivery.auth.exception;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String username) {
        super("Invalid credentials for user: " + username);
    }
}
