package com.food.delivery.cart.service;

import com.food.delivery.cart.config.KafkaTopics;
import com.food.delivery.cart.dto.AddItemRequest;
import com.food.delivery.cart.dto.MenuItemDto;
import com.food.delivery.cart.exception.*;
import com.food.delivery.cart.model.CartEntity;
import com.food.delivery.cart.model.CartItemEntity;
import com.food.delivery.cart.model.CheckoutRequestEntity;
import com.food.delivery.cart.repository.CartRepository;
import com.food.delivery.cart.repository.CheckoutRequestRepository;
import com.food.delivery.common.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    CartRepository cartRepository;

    @Mock
    CheckoutRequestRepository checkoutRequestRepository;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @Mock
    MenuItemCacheService menuItemCacheService;

    @InjectMocks
    CartService cartService;

    @Test
    void addItem_newCart_createsCartWithItem() {
        var customerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        var req = new AddItemRequest(restaurantId, menuItemId, 2);

        when(menuItemCacheService.getMenuItem(restaurantId, menuItemId))
                .thenReturn(new MenuItemDto(menuItemId, "Margherita", new BigDecimal("12.00"), true));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = cartService.addItem(req, customerId);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("Margherita");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void addItem_menuItemNotInCache_throws() {
        var customerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        var req = new AddItemRequest(restaurantId, menuItemId, 1);

        when(menuItemCacheService.getMenuItem(restaurantId, menuItemId)).thenReturn(null);

        assertThatThrownBy(() -> cartService.addItem(req, customerId))
                .isInstanceOf(MenuItemNotFoundException.class);
    }

    @Test
    void addItem_menuItemUnavailable_throws() {
        var customerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        var req = new AddItemRequest(restaurantId, menuItemId, 1);

        when(menuItemCacheService.getMenuItem(restaurantId, menuItemId))
                .thenReturn(new MenuItemDto(menuItemId, "Margherita", new BigDecimal("12.00"), false));

        assertThatThrownBy(() -> cartService.addItem(req, customerId))
                .isInstanceOf(MenuItemNotFoundException.class);
    }

    @Test
    void addItem_differentRestaurant_throws() {
        var customerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        var req = new AddItemRequest(restaurantId, menuItemId, 1);

        when(menuItemCacheService.getMenuItem(restaurantId, menuItemId))
                .thenReturn(new MenuItemDto(menuItemId, "Burger", new BigDecimal("9.00"), true));

        var existingCart = new CartEntity();
        existingCart.setId(UUID.randomUUID());
        existingCart.setCustomerId(customerId);
        existingCart.setRestaurantId(UUID.randomUUID());
        existingCart.setItems(new ArrayList<>());
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

        assertThatThrownBy(() -> cartService.addItem(req, customerId))
                .isInstanceOf(RestaurantMismatchException.class);
    }

    @Test
    void addItem_sameMenuItem_mergesQuantity() {
        var customerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        var req = new AddItemRequest(restaurantId, menuItemId, 1);

        when(menuItemCacheService.getMenuItem(restaurantId, menuItemId))
                .thenReturn(new MenuItemDto(menuItemId, "Margherita", new BigDecimal("12.00"), true));

        var existingItem = new CartItemEntity();
        existingItem.setId(UUID.randomUUID());
        existingItem.setMenuItemId(menuItemId);
        existingItem.setName("Margherita");
        existingItem.setUnitPrice(new BigDecimal("12.00"));
        existingItem.setQuantity(2);

        var cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setCustomerId(customerId);
        cart.setRestaurantId(restaurantId);
        cart.setItems(new ArrayList<>(List.of(existingItem)));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = cartService.addItem(req, customerId);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void checkout_emptyCart_throws() {
        var customerId = UUID.randomUUID();
        var cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setCustomerId(customerId);
        cart.setRestaurantId(UUID.randomUUID());
        cart.setItems(new ArrayList<>());
        when(cartRepository.findByCustomerIdForCheckout(customerId)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout(customerId, cart.getId().toString()))
                .isInstanceOf(EmptyCartException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void checkout_publishesEventAndDeletesCart() {
        var customerId = UUID.randomUUID();
        var item = new CartItemEntity();
        item.setId(UUID.randomUUID());
        item.setMenuItemId(UUID.randomUUID());
        item.setName("Margherita");
        item.setUnitPrice(new BigDecimal("12.00"));
        item.setQuantity(1);

        var cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setCustomerId(customerId);
        cart.setRestaurantId(UUID.randomUUID());
        cart.setItems(new ArrayList<>(List.of(item)));
        when(cartRepository.findByCustomerIdForCheckout(customerId)).thenReturn(Optional.of(cart));
        when(topics.getCartCheckedOut()).thenReturn("fd.cart.checked-out.v1");

        var response = cartService.checkout(customerId, cart.getId().toString());

        assertThat(response.cartId()).isEqualTo(cart.getId());
        verify(outboxService).publish(anyString(), anyString(), any());
        verify(cartRepository).delete(cart);
    }

    @Test
    void checkout_duplicateKey_returnsOriginalResponseWithoutRepublishing() {
        var customerId = UUID.randomUUID();
        var cartId = UUID.randomUUID();
        var existingRequest = new CheckoutRequestEntity();
        existingRequest.setCustomerId(customerId);
        existingRequest.setCartId(cartId);
        when(checkoutRequestRepository.findByCartId(cartId))
                .thenReturn(Optional.of(existingRequest));

        var response = cartService.checkout(customerId, cartId.toString());

        assertThat(response.cartId()).isEqualTo(cartId);
        verify(cartRepository, never()).findByCustomerIdForCheckout(any());
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void checkout_duplicateKeyForAnotherCustomer_isHidden() {
        var ownerId = UUID.randomUUID();
        var cartId = UUID.randomUUID();
        var existingRequest = new CheckoutRequestEntity();
        existingRequest.setCustomerId(ownerId);
        existingRequest.setCartId(cartId);
        when(checkoutRequestRepository.findByCartId(cartId)).thenReturn(Optional.of(existingRequest));

        assertThatThrownBy(() -> cartService.checkout(UUID.randomUUID(), cartId.toString()))
                .isInstanceOf(CartNotFoundForUserException.class);

        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void checkout_keyDoesNotMatchActiveCart_rejectsRequest() {
        var customerId = UUID.randomUUID();
        var activeCart = new CartEntity();
        activeCart.setId(UUID.randomUUID());
        activeCart.setCustomerId(customerId);
        activeCart.setRestaurantId(UUID.randomUUID());
        activeCart.setItems(new ArrayList<>(List.of(new CartItemEntity())));
        when(checkoutRequestRepository.findByCartId(any())).thenReturn(Optional.empty());
        when(cartRepository.findByCustomerIdForCheckout(customerId)).thenReturn(Optional.of(activeCart));

        assertThatThrownBy(() -> cartService.checkout(customerId, UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match the active cartId");

        verify(outboxService, never()).publish(anyString(), anyString(), any());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    void checkout_concurrentRequestCompletedWhileWaiting_returnsExistingResponse() {
        var customerId = UUID.randomUUID();
        var cartId = UUID.randomUUID();
        var existingRequest = new CheckoutRequestEntity();
        existingRequest.setCustomerId(customerId);
        existingRequest.setCartId(cartId);
        var lookupCount = new AtomicInteger();
        when(checkoutRequestRepository.findByCartId(cartId)).thenAnswer(invocation ->
                lookupCount.getAndIncrement() == 0
                        ? Optional.empty()
                        : Optional.of(existingRequest));
        when(cartRepository.findByCustomerIdForCheckout(customerId)).thenReturn(Optional.empty());

        var response = cartService.checkout(customerId, cartId.toString());

        assertThat(response.cartId()).isEqualTo(cartId);
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void checkout_cartNotFound_throws() {
        var customerId = UUID.randomUUID();
        when(cartRepository.findByCustomerIdForCheckout(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.checkout(customerId, UUID.randomUUID().toString()))
                .isInstanceOf(CartNotFoundForUserException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void removeItem_itemNotFound_throws() {
        var customerId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setCustomerId(customerId);
        cart.setRestaurantId(UUID.randomUUID());
        cart.setItems(new ArrayList<>());
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.removeItem(customerId, itemId))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessageContaining(itemId.toString());
    }

    @Test
    void updateItemQuantity_zeroRemovesItem() {
        var customerId = UUID.randomUUID();
        var item = new CartItemEntity();
        item.setId(UUID.randomUUID());
        item.setMenuItemId(UUID.randomUUID());
        item.setName("Margherita");
        item.setUnitPrice(new BigDecimal("12.00"));
        item.setQuantity(2);
        var cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setCustomerId(customerId);
        cart.setRestaurantId(UUID.randomUUID());
        cart.setItems(new ArrayList<>(List.of(item)));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        cartService.updateItemQuantity(customerId, item.getId(), 0);

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }
}
