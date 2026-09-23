package com.food.delivery.catalog.dto;

import com.food.delivery.catalog.model.MenuItemEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemResponse(UUID id, UUID restaurantId, UUID sectionId, String name, String description,
                               BigDecimal price, boolean available, int version) {
    public static MenuItemResponse from(MenuItemEntity menuItem) {
        return new MenuItemResponse(
                menuItem.getId(),
                menuItem.getRestaurantId(),
                menuItem.getSectionId(),
                menuItem.getName(),
                menuItem.getDescription(),
                menuItem.getPrice(),
                menuItem.isAvailable(),
                menuItem.getVersion()
        );
    }
}
