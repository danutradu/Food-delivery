package com.food.delivery.catalog.util;

import com.food.delivery.catalog.dto.MenuItemUpsert;
import com.food.delivery.catalog.dto.RestaurantUpsert;
import com.food.delivery.catalog.model.MenuItemEntity;
import com.food.delivery.catalog.model.RestaurantEntity;
import fd.catalog.MenuItemCreatedV1;
import fd.catalog.MenuItemDeletedV1;
import fd.catalog.MenuItemUpdatedV1;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;
import java.math.RoundingMode;

@UtilityClass
public class CatalogFactory {

    public RestaurantEntity createRestaurant(RestaurantUpsert req) {
        var restaurant = new RestaurantEntity();
        restaurant.setName(req.name());
        restaurant.setAddress(req.address());
        restaurant.setOwnerUserId(req.ownerUserId());
        restaurant.setOpen(req.isOpen());
        return restaurant;
    }

    public MenuItemEntity createMenuItem(MenuItemUpsert req) {
        var menuItem = new MenuItemEntity();
        menuItem.setName(req.name());
        menuItem.setDescription(req.description());
        menuItem.setPrice(req.price().setScale(2, RoundingMode.UNNECESSARY));
        menuItem.setSectionId(req.sectionId());
        menuItem.setAvailable(req.available());
        return menuItem;
    }

    public MenuItemCreatedV1 createMenuItemCreated(MenuItemEntity menuItem) {
        return new MenuItemCreatedV1(
                UUID.randomUUID(),
                Instant.now(),
                menuItem.getRestaurantId(),
                menuItem.getId(),
                menuItem.getName(),
                menuItem.getPrice(),
                menuItem.isAvailable()
        );
    }

    public MenuItemUpdatedV1 createMenuItemUpdated(MenuItemEntity menuItem) {
        return MenuItemUpdatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setRestaurantId(menuItem.getRestaurantId())
                .setMenuItemId(menuItem.getId())
                .setName(menuItem.getName())
                .setDescription(menuItem.getDescription())
                .setPrice(menuItem.getPrice())
                .setSectionId(menuItem.getSectionId())
                .setAvailable(menuItem.isAvailable())
                .setVersion(menuItem.getVersion())
                .build();
    }

    public MenuItemDeletedV1 createMenuItemDeleted(UUID restaurantId, UUID menuItemId) {
        return new MenuItemDeletedV1(
                UUID.randomUUID(),
                Instant.now(),
                restaurantId,
                menuItemId
        );
    }
}
