package com.food.delivery.cart.exception;

import java.util.UUID;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(UUID customerId) {
        super("Cannot checkout empty cart for user: " + customerId);
    }
}
