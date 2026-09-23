package com.food.delivery.cart.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MenuItemCacheServiceTest {

    MenuItemCacheService cache;

    @BeforeEach
    void setUp() {
        cache = new MenuItemCacheService();
    }

    @Test
    void updateAndGet_returnsItem() {
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();

        cache.updateMenuItem(restaurantId, menuItemId, "Margherita", new BigDecimal("12.00"), true);

        var item = cache.getMenuItem(restaurantId, menuItemId);
        assertThat(item).isNotNull();
        assertThat(item.name()).isEqualTo("Margherita");
        assertThat(item.price()).isEqualByComparingTo("12.00");
        assertThat(item.available()).isTrue();
    }

    @Test
    void getMenuItem_unknownRestaurant_returnsNull() {
        assertThat(cache.getMenuItem(UUID.randomUUID(), UUID.randomUUID())).isNull();
    }

    @Test
    void getMenuItem_unknownItem_returnsNull() {
        var restaurantId = UUID.randomUUID();
        cache.updateMenuItem(restaurantId, UUID.randomUUID(), "Margherita", new BigDecimal("12.00"), true);

        assertThat(cache.getMenuItem(restaurantId, UUID.randomUUID())).isNull();
    }

    @Test
    void removeMenuItem_removesItem() {
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        cache.updateMenuItem(restaurantId, menuItemId, "Margherita", new BigDecimal("12.00"), true);

        cache.removeMenuItem(restaurantId, menuItemId);

        assertThat(cache.getMenuItem(restaurantId, menuItemId)).isNull();
    }

    @Test
    void removeMenuItem_lastItem_removesRestaurantEntry() {
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        cache.updateMenuItem(restaurantId, menuItemId, "Margherita", new BigDecimal("12.00"), true);

        cache.removeMenuItem(restaurantId, menuItemId);

        // Adding a new item for same restaurant should still work (no stale empty map)
        cache.updateMenuItem(restaurantId, UUID.randomUUID(), "Pepperoni", new BigDecimal("14.00"), true);
        assertThat(cache.getMenuItem(restaurantId, menuItemId)).isNull();
    }

    @Test
    void updateMenuItem_overwritesExistingItem() {
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        cache.updateMenuItem(restaurantId, menuItemId, "Margherita", new BigDecimal("12.00"), true);
        cache.updateMenuItem(restaurantId, menuItemId, "Margherita Special", new BigDecimal("15.00"), false);

        var item = cache.getMenuItem(restaurantId, menuItemId);
        assertThat(item.name()).isEqualTo("Margherita Special");
        assertThat(item.price()).isEqualByComparingTo("15.00");
        assertThat(item.available()).isFalse();
    }
}
