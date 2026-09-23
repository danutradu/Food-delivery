package com.food.delivery.catalog.exception;

import java.util.UUID;

public class RestaurantNotFoundException extends RuntimeException {
    public RestaurantNotFoundException(UUID id) {
        super("Restaurant not found: " + id);
    }
}
