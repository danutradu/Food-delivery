package com.food.delivery.cart.exception;

import java.util.UUID;

public class MenuItemNotFoundException extends RuntimeException {
    public MenuItemNotFoundException(UUID restaurantId, UUID menuItemId) {
        super("Menu item not found restaurantId=" + restaurantId + " menuItemId=" + menuItemId);
    }
}
