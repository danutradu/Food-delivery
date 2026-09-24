package com.food.delivery.delivery.exception;

import java.util.UUID;

public class CourierNotFoundException extends RuntimeException {
    public CourierNotFoundException(UUID id) {
        super("Courier not found: " + id);
    }
}
