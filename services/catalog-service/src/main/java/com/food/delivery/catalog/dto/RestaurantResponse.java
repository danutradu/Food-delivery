package com.food.delivery.catalog.dto;

import com.food.delivery.catalog.model.RestaurantEntity;

import java.util.UUID;

public record RestaurantResponse(UUID id, UUID ownerUserId, String name, String address, boolean open) {
    public static RestaurantResponse from(RestaurantEntity restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getOwnerUserId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.isOpen()
        );
    }
}
