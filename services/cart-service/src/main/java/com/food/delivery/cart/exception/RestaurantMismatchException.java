package com.food.delivery.cart.exception;

import java.util.UUID;

public class RestaurantMismatchException extends RuntimeException {
    public RestaurantMismatchException(UUID currentRestaurantId, UUID newRestaurantId) {
        super("Cannot add items from different restaurants. Current: " + currentRestaurantId + ", New: " + newRestaurantId);
    }
}
