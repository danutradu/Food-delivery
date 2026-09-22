package com.food.delivery.auth.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String value) {
        super("User already exists: " + value);
    }
}
