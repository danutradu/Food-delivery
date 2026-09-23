package com.food.delivery.cart.service;

import com.food.delivery.cart.config.KafkaTopics;
import com.food.delivery.cart.dto.AddItemRequest;
import com.food.delivery.cart.dto.CartResponse;
import com.food.delivery.cart.dto.CheckoutResponse;
import com.food.delivery.cart.exception.*;
import com.food.delivery.cart.model.CartEntity;
import com.food.delivery.cart.model.CheckoutRequestEntity;
import com.food.delivery.cart.repository.CartRepository;
import com.food.delivery.cart.repository.CheckoutRequestRepository;
import com.food.delivery.cart.util.CartFactory;
import com.food.delivery.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CheckoutRequestRepository checkoutRequestRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;
    private final MenuItemCacheService menuItemCacheService;

    public CartResponse addItem(AddItemRequest req, UUID customerId) {
        log.info("Adding item to cart customerId={} restaurantId={} menuItemId={} quantity={}", customerId, req.restaurantId(), req.menuItemId(), req.quantity());

        var menuItem = menuItemCacheService.getMenuItem(req.restaurantId(), req.menuItemId());
        if (menuItem == null || !menuItem.available()) {
            throw new MenuItemNotFoundException(req.restaurantId(), req.menuItemId());
        }

        var cart = cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            var nc = new CartEntity();
            nc.setCustomerId(customerId);
            nc.setRestaurantId(req.restaurantId());
            return nc;
        });

        if (!cart.getRestaurantId().equals(req.restaurantId())) {
            throw new RestaurantMismatchException(cart.getRestaurantId(), req.restaurantId());
        }

        var existingItem = cart.getItems().stream()
                .filter(item -> item.getMenuItemId().equals(req.menuItemId()))
                .findFirst();

        if (existingItem.isPresent()) {
            var item = existingItem.get();
            int oldQuantity = item.getQuantity();
            item.setQuantity(oldQuantity + req.quantity());
            log.info("Merged with existing item menuItemId={} oldQuantity={} added={} newQuantity={}", req.menuItemId(), oldQuantity, req.quantity(), item.getQuantity());
        } else {
            var item = CartFactory.createCartItem(req.menuItemId(), menuItem.name(), menuItem.price(), req.quantity());
            item.setCart(cart);
            cart.getItems().add(item);
            log.info("Added new item to cart menuItemId={} quantity={}", req.menuItemId(), req.quantity());
        }

        var savedCart = cartRepository.save(cart);
        return CartFactory.createCartResponse(savedCart);
    }

    public CartResponse getCart(UUID customerId) {
        var cart = cartRepository.findByCustomerId(customerId).orElse(null);
        return cart != null ? CartFactory.createCartResponse(cart) : null;
    }

    public CartResponse updateItemQuantity(UUID customerId, UUID itemId, int quantity) {
        log.info("Updating item quantity customerId={} itemId={} quantity={}", customerId, itemId, quantity);

        var cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundForUserException(customerId));

        var item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(itemId));

        if (quantity <= 0) {
            cart.getItems().remove(item);
            log.info("Item removed due to zero quantity itemId={}", itemId);
        } else {
            item.setQuantity(quantity);
            log.info("Item quantity updated itemId={} newQuantity={}", itemId, quantity);
        }

        var savedCart = cartRepository.save(cart);
        return CartFactory.createCartResponse(savedCart);
    }

    public CartResponse removeItem(UUID customerId, UUID itemId) {
        log.info("Removing item customerId={} itemId={}", customerId, itemId);

        var cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundForUserException(customerId));

        var removed = cart.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new CartItemNotFoundException(itemId);
        }

        log.info("Item removed from cart itemId={}", itemId);
        var savedCart = cartRepository.save(cart);
        return CartFactory.createCartResponse(savedCart);
    }

    public void clearCart(UUID customerId) {
        log.info("Clearing cart customerId={}", customerId);

        var cart = cartRepository.findByCustomerId(customerId);
        if (cart.isPresent()) {
            cartRepository.delete(cart.get());
            log.info("Cart cleared customerId={}", customerId);
        }
    }

    @Transactional
    public CheckoutResponse checkout(UUID customerId, String idempotencyKey) {
        final UUID requestedCartId;
        try {
            requestedCartId = UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Idempotency-Key must be the cartId UUID", e);
        }

        var existingRequest = checkoutRequestRepository.findByCartId(requestedCartId);
        if (existingRequest.isPresent()) {
            return existingCheckoutResponse(customerId, existingRequest.get());
        }

        var cart = cartRepository.findByCustomerIdForCheckout(customerId).orElse(null);
        // Re-check after acquiring the cart lock in case another checkout committed while this request waited.
        existingRequest = checkoutRequestRepository.findByCartId(requestedCartId);
        if (existingRequest.isPresent()) {
            return existingCheckoutResponse(customerId, existingRequest.get());
        }

        log.info("CartCheckedOut customerId={} cartId={}", customerId, requestedCartId);

        if (cart == null) {
            throw new CartNotFoundForUserException(customerId);
        }
        if (!cart.getId().equals(requestedCartId)) {
            throw new IllegalArgumentException("Idempotency-Key must match the active cartId");
        }

        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException(customerId);
        }

        var event = CartFactory.createCartCheckedOut(cart);
        var checkoutRequest = new CheckoutRequestEntity();
        checkoutRequest.setCustomerId(customerId);
        checkoutRequest.setCartId(event.getCartId());
        checkoutRequestRepository.save(checkoutRequest);

        outboxService.publish(topics.getCartCheckedOut(), event.getCartId().toString(), event);

        cartRepository.delete(cart);
        log.info("Cart checked out and deleted cartId={} customerId={} items={} total={} restaurantId={}",
                cart.getId(), customerId, cart.getItemCount(), cart.getTotal(), cart.getRestaurantId());
        return new CheckoutResponse(event.getCartId());
    }

    private CheckoutResponse existingCheckoutResponse(UUID customerId, CheckoutRequestEntity request) {
        if (!request.getCustomerId().equals(customerId)) {
            throw new CartNotFoundForUserException(customerId);
        }
        log.info("Returning existing checkout request customerId={} cartId={}", customerId, request.getCartId());
        return new CheckoutResponse(request.getCartId());
    }
}
