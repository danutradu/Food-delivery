package com.food.delivery.cart.service;

import com.food.delivery.cart.dto.MenuItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MenuItemCacheService {

    private final Map<String, Map<String, MenuItemDto>> menuItemsByRestaurant = new ConcurrentHashMap<>();

    public MenuItemDto getMenuItem(UUID restaurantId, UUID menuItemId) {
        var menuItems = menuItemsByRestaurant.get(restaurantId.toString());
        if (menuItems == null) {
            log.debug("Restaurant {} not found in cache", restaurantId);
            return null;
        }
        var item = menuItems.get(menuItemId.toString());
        if (item == null) {
            log.debug("Menu item {} not found in cache for restaurantId {}", menuItemId, restaurantId);
        }

        return item;
    }

    public void updateMenuItem(UUID restaurantId, UUID menuItemId, String name, BigDecimal price, boolean available) {
        var item = new MenuItemDto(menuItemId, name, price, available);
        menuItemsByRestaurant.computeIfAbsent(restaurantId.toString(), k -> new ConcurrentHashMap<>())
                .put(menuItemId.toString(), item);
        log.debug("Updated menu item cache restaurantId={} menuItemId={} name={} available={}", restaurantId, menuItemId, name, available);
    }

    public void removeMenuItem(UUID restaurantId, UUID menuItemId) {
        var menuItems = menuItemsByRestaurant.get(restaurantId.toString());
        if (menuItems != null) {
            menuItems.remove(menuItemId.toString());

            if (menuItems.isEmpty()) {
                menuItemsByRestaurant.remove(restaurantId.toString());
            }
        }
        log.debug("Removed menu item from cache restaurantId={} menuItemId={}", restaurantId, menuItemId);
    }
}
