package com.food.delivery.catalog.service;

import com.food.delivery.catalog.config.KafkaTopics;
import com.food.delivery.catalog.dto.MenuItemResponse;
import com.food.delivery.catalog.dto.MenuItemUpsert;
import com.food.delivery.catalog.dto.MenuSectionResponse;
import com.food.delivery.catalog.dto.MenuSectionUpsert;
import com.food.delivery.catalog.dto.RestaurantResponse;
import com.food.delivery.catalog.dto.RestaurantUpsert;
import com.food.delivery.catalog.exception.MenuItemNotFoundException;
import com.food.delivery.catalog.exception.MenuSectionConflictException;
import com.food.delivery.catalog.exception.MenuSectionNotFoundException;
import com.food.delivery.catalog.exception.RestaurantNotFoundException;
import com.food.delivery.catalog.model.MenuSectionEntity;
import com.food.delivery.catalog.repository.MenuItemRepository;
import com.food.delivery.catalog.repository.MenuSectionRepository;
import com.food.delivery.catalog.repository.RestaurantRepository;
import com.food.delivery.catalog.util.CatalogFactory;
import com.food.delivery.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuSectionRepository menuSectionRepository;
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
    public MenuItemResponse createMenuItem(UUID restaurantId, MenuItemUpsert req, UUID callerUserId, boolean admin) {
        log.info("MenuItemCreate restaurantId={} name={}", restaurantId, req.name());

        verifyOwnership(restaurantId, callerUserId, admin);
        validateSection(restaurantId, req.sectionId());

        var menuItem = CatalogFactory.createMenuItem(req);
        menuItem.setRestaurantId(restaurantId);
        menuItemRepository.save(menuItem);

        var event = CatalogFactory.createMenuItemCreated(menuItem);
        outboxService.publish(topics.getMenuItemCreated(), event.getMenuItemId().toString(), event);

        log.info("Menu item created menuItemId={}", menuItem.getId());
        return MenuItemResponse.from(menuItem);
    }

    @Transactional
    public MenuItemResponse updateMenuItem(UUID restaurantId, UUID menuItemId, MenuItemUpsert req,
                                           UUID callerUserId, boolean admin) {
        log.info("MenuItemUpdate restaurantId={} menuItemId={} name={}", restaurantId, menuItemId, req.name());

        verifyOwnership(restaurantId, callerUserId, admin);
        validateSection(restaurantId, req.sectionId());

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
    public MenuSectionResponse createMenuSection(UUID restaurantId, MenuSectionUpsert req,
                                                 UUID callerUserId, boolean admin) {
        verifyOwnership(restaurantId, callerUserId, admin);
        if (menuSectionRepository.existsByRestaurantIdAndName(restaurantId, req.name())) {
            throw new MenuSectionConflictException("A menu section with this name already exists");
        }

        var section = new MenuSectionEntity();
        section.setRestaurantId(restaurantId);
        section.setName(req.name());
        section.setDisplayOrder(req.displayOrder());
        return MenuSectionResponse.from(menuSectionRepository.save(section));
    }

    @Transactional(readOnly = true)
    public List<MenuSectionResponse> getMenuSections(UUID restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException(restaurantId);
        }
        return menuSectionRepository.findByRestaurantIdOrderByDisplayOrderAscNameAsc(restaurantId).stream()
                .map(MenuSectionResponse::from)
                .toList();
    }

    @Transactional
    public MenuSectionResponse updateMenuSection(UUID restaurantId, UUID sectionId,
                                                 MenuSectionUpsert req, UUID callerUserId, boolean admin) {
        verifyOwnership(restaurantId, callerUserId, admin);
        var section = findSection(sectionId);
        verifySectionBelongsToRestaurant(section, restaurantId);
        if (menuSectionRepository.existsByRestaurantIdAndNameAndIdNot(restaurantId, req.name(), sectionId)) {
            throw new MenuSectionConflictException("A menu section with this name already exists");
        }
        section.setName(req.name());
        section.setDisplayOrder(req.displayOrder());
        return MenuSectionResponse.from(menuSectionRepository.save(section));
    }

    @Transactional
    public void deleteMenuSection(UUID restaurantId, UUID sectionId, UUID callerUserId, boolean admin) {
        verifyOwnership(restaurantId, callerUserId, admin);
        var section = findSection(sectionId);
        verifySectionBelongsToRestaurant(section, restaurantId);
        if (menuItemRepository.existsBySectionId(sectionId)) {
            throw new MenuSectionConflictException("Menu section still contains menu items");
        }
        menuSectionRepository.delete(section);
    }

    @Transactional
    public void deleteMenuItem(UUID restaurantId, UUID itemId, UUID callerUserId, boolean admin) {
        log.info("MenuItemDeleted restaurantId={} itemId={}", restaurantId, itemId);

        verifyOwnership(restaurantId, callerUserId, admin);

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
    public MenuItemResponse setMenuItemAvailability(UUID restaurantId, UUID itemId, boolean available,
                                                    UUID callerUserId, boolean admin) {
        log.info("MenuItemAvailability restaurantId={} itemId={} available={}", restaurantId, itemId, available);

        verifyOwnership(restaurantId, callerUserId, admin);

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
    public RestaurantResponse setRestaurantStatus(UUID restaurantId, boolean open, UUID callerUserId, boolean admin) {
        log.info("RestaurantStatus restaurantId={} open={}", restaurantId, open);

        verifyOwnership(restaurantId, callerUserId, admin);

        var restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));

        restaurant.setOpen(open);
        restaurantRepository.save(restaurant);

        log.info("Restaurant status updated restaurantId={} open={}", restaurantId, open);
        return RestaurantResponse.from(restaurant);
    }

    private void verifyOwnership(UUID restaurantId, UUID callerUserId, boolean admin) {
        var restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
        if (!admin && !restaurant.getOwnerUserId().equals(callerUserId)) {
            throw new IllegalArgumentException("You do not own this restaurant");
        }
    }

    private void validateSection(UUID restaurantId, UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        verifySectionBelongsToRestaurant(findSection(sectionId), restaurantId);
    }

    private MenuSectionEntity findSection(UUID sectionId) {
        return menuSectionRepository.findById(sectionId)
                .orElseThrow(() -> new MenuSectionNotFoundException(sectionId));
    }

    private void verifySectionBelongsToRestaurant(MenuSectionEntity section, UUID restaurantId) {
        if (!section.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Menu section does not belong to restaurant");
        }
    }
}
