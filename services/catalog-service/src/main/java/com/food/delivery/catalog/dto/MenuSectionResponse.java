package com.food.delivery.catalog.dto;

import com.food.delivery.catalog.model.MenuSectionEntity;

import java.util.UUID;

public record MenuSectionResponse(UUID id, UUID restaurantId, String name, int displayOrder) {
    public static MenuSectionResponse from(MenuSectionEntity section) {
        return new MenuSectionResponse(
                section.getId(),
                section.getRestaurantId(),
                section.getName(),
                section.getDisplayOrder());
    }
}
