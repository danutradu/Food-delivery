package com.food.delivery.catalog.service;

import com.food.delivery.catalog.config.KafkaTopics;
import com.food.delivery.catalog.dto.MenuItemUpsert;
import com.food.delivery.catalog.dto.MenuSectionUpsert;
import com.food.delivery.catalog.dto.RestaurantUpsert;
import com.food.delivery.catalog.exception.MenuItemNotFoundException;
import com.food.delivery.catalog.exception.MenuSectionConflictException;
import com.food.delivery.catalog.exception.RestaurantNotFoundException;
import com.food.delivery.catalog.model.MenuItemEntity;
import com.food.delivery.catalog.model.MenuSectionEntity;
import com.food.delivery.catalog.model.RestaurantEntity;
import com.food.delivery.catalog.repository.MenuItemRepository;
import com.food.delivery.catalog.repository.MenuSectionRepository;
import com.food.delivery.catalog.repository.RestaurantRepository;
import com.food.delivery.common.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    RestaurantRepository restaurantRepository;

    @Mock
    MenuItemRepository menuItemRepository;

    @Mock
    MenuSectionRepository menuSectionRepository;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @InjectMocks
    CatalogService catalogService;

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

        var result = catalogService.createMenuItem(restaurantId, req, ownerId, false);

        assertThat(result.name()).isEqualTo("Margherita");
        assertThat(result.restaurantId()).isEqualTo(restaurantId);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void createMenuSection_savesRestaurantSection() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuSectionRepository.save(any())).thenAnswer(i -> {
            var saved = (MenuSectionEntity) i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = catalogService.createMenuSection(restaurantId,
                new MenuSectionUpsert("Pizzas", 1), ownerId, false);

        assertThat(result.restaurantId()).isEqualTo(restaurantId);
        assertThat(result.name()).isEqualTo("Pizzas");
        assertThat(result.displayOrder()).isEqualTo(1);
    }

    @Test
    void adminCanCreateMenuSectionForAnotherRestaurant() {
        var ownerId = UUID.randomUUID();
        var adminId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuSectionRepository.save(any())).thenAnswer(i -> {
            var saved = (MenuSectionEntity) i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = catalogService.createMenuSection(restaurantId,
                new MenuSectionUpsert("Drinks", 2), adminId, true);

        assertThat(result.name()).isEqualTo("Drinks");
        verify(menuSectionRepository).save(any(MenuSectionEntity.class));
    }

    @Test
    void createMenuItem_rejectsSectionFromAnotherRestaurant() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var section = new MenuSectionEntity();
        section.setId(UUID.randomUUID());
        section.setRestaurantId(UUID.randomUUID());
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> catalogService.createMenuItem(restaurantId,
                new MenuItemUpsert(section.getId(), "Pizza", null, new BigDecimal("12.00"), true, 0), ownerId, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to restaurant");
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void deleteMenuSection_rejectsSectionContainingItems() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var sectionId = UUID.randomUUID();
        var section = new MenuSectionEntity();
        section.setId(sectionId);
        section.setRestaurantId(restaurantId);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(menuItemRepository.existsBySectionId(sectionId)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.deleteMenuSection(restaurantId, sectionId, ownerId, false))
                .isInstanceOf(MenuSectionConflictException.class)
                .hasMessageContaining("still contains menu items");
        verify(menuSectionRepository, never()).delete(any());
    }

    @Test
    void updateMenuItem_notFound_throws() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.updateMenuItem(restaurantId, itemId, new MenuItemUpsert(null, "x", null, new BigDecimal("1.00"), true, 0), ownerId, false))
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

        assertThatThrownBy(() -> catalogService.updateMenuItem(restaurantId, itemId, new MenuItemUpsert(null, "x", null, new BigDecimal("1.00"), true, 0), ownerId, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteMenuItem_notFound_throws() {
        var ownerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));
        when(menuItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.deleteMenuItem(restaurantId, itemId, ownerId, false))
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

        var result = catalogService.setMenuItemAvailability(restaurantId, itemId, false, ownerId, false);

        assertThat(result.available()).isFalse();
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void setRestaurantStatus_notFound_throws() {
        var id = UUID.randomUUID();
        var callerUserId = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.setRestaurantStatus(id, false, callerUserId, false))
                .isInstanceOf(RestaurantNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void createMenuItem_wrongOwner_throws() {
        var ownerId = UUID.randomUUID();
        var wrongCaller = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(stubRestaurant(restaurantId, ownerId)));

        assertThatThrownBy(() -> catalogService.createMenuItem(restaurantId, new MenuItemUpsert(null, "x", null, new BigDecimal("1.00"), true, 0), wrongCaller, false))
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
                new MenuItemUpsert(null, "New", "Updated", new BigDecimal("15.00"), false, 0), ownerId, false);

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

        catalogService.deleteMenuItem(restaurantId, itemId, ownerId, false);

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

        var result = catalogService.setRestaurantStatus(restaurantId, false, ownerId, false);

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
