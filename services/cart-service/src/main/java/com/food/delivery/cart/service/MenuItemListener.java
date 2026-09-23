package com.food.delivery.cart.service;

import com.food.delivery.cart.repository.CartItemRepository;
import com.food.delivery.common.config.StandardRetryableTopic;
import com.food.delivery.common.dlt.DltEventService;
import fd.catalog.MenuItemCreatedV1;
import fd.catalog.MenuItemDeletedV1;
import fd.catalog.MenuItemUpdatedV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MenuItemListener {

    private final MenuItemCacheService menuItemCacheService;
    private final CartItemRepository cartItemRepository;
    private final DltEventService dltEventService;

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.menu-item-created}", groupId = "${kafka.consumer.group-id}")
    public void onMenuItemCreated(MenuItemCreatedV1 event) {
        log.info("Received menu item created event restaurantId={} menuItemId={}", event.getRestaurantId(), event.getMenuItemId());

        menuItemCacheService.updateMenuItem(
                event.getRestaurantId(),
                event.getMenuItemId(),
                event.getName(),
                event.getPrice(),
                event.getAvailable()
        );
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.menu-item-updated}", groupId = "${kafka.consumer.group-id}")
    @Transactional
    public void onMenuItemUpdated(MenuItemUpdatedV1 event) {
        log.info("Received menu item updated event restaurantId={} menuItemId={}", event.getRestaurantId(), event.getMenuItemId());

        menuItemCacheService.updateMenuItem(
                event.getRestaurantId(),
                event.getMenuItemId(),
                event.getName(),
                event.getPrice(),
                event.getAvailable()
        );

        int updatedItems = cartItemRepository.updateByMenuItemId(
                event.getMenuItemId(),
                event.getName(),
                event.getPrice()
        );

        if (updatedItems > 0) {
            log.info("Updated {} cart items with new menu item data menuItemId={} newPrice={}", updatedItems, event.getMenuItemId(), event.getPrice());
        }
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.menu-item-deleted}", groupId = "${kafka.consumer.group-id}")
    public void onMenuItemDeleted(MenuItemDeletedV1 event) {
        log.info("Received menu item deleted event restaurantId={} menuItemId={}", event.getRestaurantId(), event.getMenuItemId());

        menuItemCacheService.removeMenuItem(event.getRestaurantId(), event.getMenuItemId());
    }

    @DltHandler
    public void handleDlt(Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String originalKey,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset) {
        dltEventService.park("cart-service", topic, originalTopic, originalKey,
                originalPartition, originalOffset, event);
    }
}
