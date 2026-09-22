package com.food.delivery.auth.exception;

public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException(String username) {
        super("Account is disabled for user: " + username);
    }
}
