package com.food.delivery.cart.exception;

import java.util.UUID;

public class CartNotFoundForUserException extends RuntimeException {
    public CartNotFoundForUserException(UUID customerId) {
        super("No cart found for user: " + customerId);
    }
}
