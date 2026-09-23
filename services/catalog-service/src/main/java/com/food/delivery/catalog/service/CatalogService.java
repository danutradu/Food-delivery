package com.food.delivery.catalog.service;

import com.food.delivery.catalog.config.KafkaTopics;
import com.food.delivery.catalog.dto.MenuItemResponse;
import com.food.delivery.catalog.dto.MenuItemUpsert;
import com.food.delivery.catalog.dto.RestaurantResponse;
import com.food.delivery.catalog.dto.RestaurantUpsert;
import com.food.delivery.catalog.exception.MenuItemNotFoundException;
import com.food.delivery.catalog.exception.RestaurantNotFoundException;
import com.food.delivery.catalog.repository.MenuItemRepository;
import com.food.delivery.catalog.repository.RestaurantRepository;
import com.food.delivery.catalog.util.CatalogFactory;
import com.food.delivery.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    @Transactional
    public RestaurantResponse createRestaurant(RestaurantUpsert req, UUID callerUserId) {
        log.info("RestaurantCreated name={} ownerUserId={}", req.name(), callerUserId);

        var restaurant = CatalogFactory.createRestaurant(req);
        restaurant.setOwnerUserId(callerUserId);
        restaurantRepository.save(restaurant);

        log.info("Restaurant created restaurantId={}", restaurant.getId());
        return RestaurantResponse.from(restaurant);
    }

    @Transactional
    public MenuItemResponse createMenuItem(UUID restaurantId, MenuItemUpsert req, UUID callerUserId) {
        log.info("MenuItemCreate restaurantId={} name={}", restaurantId, req.name());

        verifyOwnership(restaurantId, callerUserId);

        var menuItem = CatalogFactory.createMenuItem(req);
        menuItem.setRestaurantId(restaurantId);
        menuItemRepository.save(menuItem);

        var event = CatalogFactory.createMenuItemCreated(menuItem);
        outboxService.publish(topics.getMenuItemCreated(), event.getMenuItemId().toString(), event);

        log.info("Menu item created menuItemId={}", menuItem.getId());
        return MenuItemResponse.from(menuItem);
    }

    @Transactional
    public MenuItemResponse updateMenuItem(UUID restaurantId, UUID menuItemId, MenuItemUpsert req, UUID callerUserId) {
        log.info("MenuItemUpdate restaurantId={} menuItemId={} name={}", restaurantId, menuItemId, req.name());

        verifyOwnership(restaurantId, callerUserId);

        var menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException(menuItemId));

        if (!menuItem.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Menu item does not belong to restaurant");
        }

        menuItem.setName(req.name());
        menuItem.setDescription(req.description());
        menuItem.setPrice(req.price().setScale(2, RoundingMode.UNNECESSARY));
        menuItem.setSectionId(req.sectionId());
        menuItem.setAvailable(req.available());

        menuItemRepository.save(menuItem);

        var event = CatalogFactory.createMenuItemUpdated(menuItem);
        outboxService.publish(topics.getMenuItemUpdated(), event.getMenuItemId().toString(), event);

        log.info("Menu item updated menuItemId={}", menuItem.getId());
        return MenuItemResponse.from(menuItem);
    }

    public Page<RestaurantResponse> getAllRestaurants(Pageable pageable) {
        return restaurantRepository.findAll(pageable).map(RestaurantResponse::from);
    }

    public RestaurantResponse getRestaurant(UUID id) {
        return restaurantRepository.findById(id)
                .map(RestaurantResponse::from)
                .orElseThrow(() -> new RestaurantNotFoundException(id));
    }

    public Page<MenuItemResponse> getMenu(UUID restaurantId, Pageable pageable) {
        return menuItemRepository.findByRestaurantId(restaurantId, pageable).map(MenuItemResponse::from);
    }

    @Transactional
    public void deleteMenuItem(UUID restaurantId, UUID itemId, UUID callerUserId) {
        log.info("MenuItemDeleted restaurantId={} itemId={}", restaurantId, itemId);

        verifyOwnership(restaurantId, callerUserId);

        var menuItem = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new MenuItemNotFoundException(itemId));

        if (!menuItem.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Menu item does not belong to restaurant");
        }

        menuItemRepository.delete(menuItem);

        var event = CatalogFactory.createMenuItemDeleted(restaurantId, itemId);
        outboxService.publish(topics.getMenuItemDeleted(), event.getMenuItemId().toString(), event);

        log.info("Menu item deleted itemId={}", itemId);
    }

    @Transactional
    public MenuItemResponse setMenuItemAvailability(UUID restaurantId, UUID itemId, boolean available, UUID callerUserId) {
        log.info("MenuItemAvailability restaurantId={} itemId={} available={}", restaurantId, itemId, available);

        verifyOwnership(restaurantId, callerUserId);

        var menuItem = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new MenuItemNotFoundException(itemId));

        if (!menuItem.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Menu item does not belong to restaurant");
        }

        menuItem.setAvailable(available);
        menuItemRepository.save(menuItem);

        var event = CatalogFactory.createMenuItemUpdated(menuItem);
        outboxService.publish(topics.getMenuItemUpdated(), event.getMenuItemId().toString(), event);

        log.info("Published menu item availability event itemId={} available={}", itemId, available);
        return MenuItemResponse.from(menuItem);
    }

    @Transactional
    public RestaurantResponse setRestaurantStatus(UUID restaurantId, boolean open, UUID callerUserId) {
        log.info("RestaurantStatus restaurantId={} open={}", restaurantId, open);

        verifyOwnership(restaurantId, callerUserId);

        var restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));

        restaurant.setOpen(open);
        restaurantRepository.save(restaurant);

        log.info("Restaurant status updated restaurantId={} open={}", restaurantId, open);
        return RestaurantResponse.from(restaurant);
    }

    private void verifyOwnership(UUID restaurantId, UUID callerUserId) {
        var restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
        if (!restaurant.getOwnerUserId().equals(callerUserId)) {
            throw new IllegalArgumentException("You do not own this restaurant");
        }
    }
}
