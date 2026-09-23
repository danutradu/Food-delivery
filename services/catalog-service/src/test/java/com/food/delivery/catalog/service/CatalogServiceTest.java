package com.food.delivery.catalog.service;

import com.food.delivery.catalog.config.KafkaTopics;
import com.food.delivery.catalog.dto.MenuItemUpsert;
import com.food.delivery.catalog.dto.RestaurantUpsert;
import com.food.delivery.catalog.exception.MenuItemNotFoundException;
import com.food.delivery.catalog.exception.RestaurantNotFoundException;
import com.food.delivery.catalog.model.MenuItemEntity;
import com.food.delivery.catalog.repository.MenuItemRepository;
import com.food.delivery.catalog.repository.RestaurantRepository;
import com.food.delivery.common.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.food.delivery.catalog.model.RestaurantEntity;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock OutboxService outboxService;
    @Mock KafkaTopics topics;

    @InjectMocks CatalogService catalogService;

    // RestaurantUpsert(ownerUserId, name, address, isOpen)
    // MenuItemUpsert(sectionId, name, description, price, available, version)

    private RestaurantEntity stubRestaurant(UUID restaurantId, UUID ownerId) {
        var r = new RestaurantEntity();
        r.setId(restaurantId);
        r.setOwnerUserId(ownerId);
        r.setName("Stub");
        r.setAddress("Addr");
        return r;
    }

    @Test
    void createRestaurant_savesRestaurant() {
        var callerUserId = UUID.randomUUID();
        var req = new RestaurantUpsert(callerUserId, "Pizza Palace", "123 Main St", true);
        when(restaurantRepository.save(any())).thenAnswer(i -> {
            var saved = (RestaurantEntity) i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        var result = catalogService.createRestaurant(req, callerUserId);

        assertThat(result.name()).isEqualTo("Pizza Palace");
        assertThat(result.ownerUserId()).isEqualTo(callerUserId);
    }

    @Test
    void getRestaurant_notFound_throws() {
        var id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.getRestaurant(id))
                .isInstanceOf(RestaurantNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void createMenuItem_savesAndPublishesEvent() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        var req = new MenuItemUpsert(null, "Margherita", "Classic pizza", new BigDecimal("12.00"), true, 0);
        when(menuItemRepository.save(any())).thenAnswer(i -> {
            var saved = (MenuItemEntity) i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(topics.getMenuItemCreated()).thenReturn("fd.catalog.menu-item-created.v1");

        var result = catalogService.createMenuItem(restaurantId, req, ownerId);

        assertThat(result.name()).isEqualTo("Margherita");
        assertThat(result.restaurantId()).isEqualTo(restaurantId);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void updateMenuItem_notFound_throws() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.updateMenuItem(restaurantId, itemId, new MenuItemUpsert(null, "x", null, new BigDecimal("1.00"), true, 0), ownerId))
                .isInstanceOf(MenuItemNotFoundException.class)
                .hasMessageContaining(itemId.toString());
    }

    @Test
    void updateMenuItem_restaurantMismatch_throws() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        var item = new MenuItemEntity();
        item.setId(itemId);
        item.setRestaurantId(UUID.randomUUID()); // different restaurant
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> catalogService.updateMenuItem(restaurantId, itemId, new MenuItemUpsert(null, "x", null, new BigDecimal("1.00"), true, 0), ownerId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteMenuItem_notFound_throws() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.deleteMenuItem(restaurantId, itemId, ownerId))
                .isInstanceOf(MenuItemNotFoundException.class)
                .hasMessageContaining(itemId.toString());
    }

    @Test
    void setMenuItemAvailability_updatesAndPublishes() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        var item = new MenuItemEntity();
        item.setId(itemId);
        item.setRestaurantId(restaurantId);
        item.setName("Margherita");
        item.setPrice(new BigDecimal("10.00"));
        item.setAvailable(true);
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getMenuItemUpdated()).thenReturn("fd.catalog.menu-item-updated.v1");

        var result = catalogService.setMenuItemAvailability(restaurantId, itemId, false, ownerId);

        assertThat(result.available()).isFalse();
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void setRestaurantStatus_notFound_throws() {
        var id = UUID.randomUUID();
        var callerUserId = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.setRestaurantStatus(id, false, callerUserId))
                .isInstanceOf(RestaurantNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void createMenuItem_wrongOwner_throws() {
        var ownerId = UUID.randomUUID();
        var wrongCaller = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));

        assertThatThrownBy(() -> catalogService.createMenuItem(restaurantId, new MenuItemUpsert(null, "x", null, new BigDecimal("1.00"), true, 0), wrongCaller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not own");
    }

    @Test
    void updateMenuItem_ownedItem_updatesAllEditableFieldsAndPublishesEvent() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var item = new MenuItemEntity();
        item.setId(itemId);
        item.setRestaurantId(restaurantId);
        item.setName("Old");
        item.setPrice(new BigDecimal("10.00"));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getMenuItemUpdated()).thenReturn("fd.catalog.menu-item-updated.v1");

        var result = catalogService.updateMenuItem(restaurantId, itemId,
                new MenuItemUpsert(UUID.randomUUID(), "New", "Updated", new BigDecimal("15.00"), false, 0), ownerId);

        assertThat(result.name()).isEqualTo("New");
        assertThat(result.price()).isEqualByComparingTo("15.00");
        assertThat(result.available()).isFalse();
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void deleteMenuItem_ownedItem_deletesAndPublishesEvent() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var item = new MenuItemEntity();
        item.setId(itemId);
        item.setRestaurantId(restaurantId);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(topics.getMenuItemDeleted()).thenReturn("fd.catalog.menu-item-deleted.v1");

        catalogService.deleteMenuItem(restaurantId, itemId, ownerId);

        verify(menuItemRepository).delete(item);
        verify(outboxService).publish(anyString(), eq(itemId.toString()), any());
    }

    @Test
    void setRestaurantStatus_ownedRestaurant_updatesOpenState() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var restaurant = stubRestaurant(restaurantId, ownerId);
        restaurant.setOpen(true);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = catalogService.setRestaurantStatus(restaurantId, false, ownerId);

        assertThat(result.open()).isFalse();
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    void getMenu_returnsPagedItemsForRestaurant() {
        var restaurantId = UUID.randomUUID();
        var pageable = Pageable.ofSize(10);
        var item = new MenuItemEntity();
        item.setId(UUID.randomUUID());
        item.setRestaurantId(restaurantId);
        item.setName("Margherita");
        item.setPrice(new BigDecimal("12.00"));
        when(menuItemRepository.findByRestaurantId(restaurantId, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        var result = catalogService.getMenu(restaurantId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Margherita");
    }
}
