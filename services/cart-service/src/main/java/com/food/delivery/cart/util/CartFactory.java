package com.food.delivery.cart.util;

import com.food.delivery.cart.dto.CartItemResponse;
import com.food.delivery.cart.dto.CartResponse;
import com.food.delivery.cart.model.CartEntity;
import com.food.delivery.cart.model.CartItemEntity;
import fd.cart.CartCheckedOutV1;
import fd.cart.CartItem;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class CartFactory {

    public CartItemEntity createCartItem(UUID menuItemId, String name, BigDecimal unitPrice, int quantity) {
        var item = new CartItemEntity();
        item.setMenuItemId(menuItemId);
        item.setName(name);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        return item;
    }

    public CartCheckedOutV1 createCartCheckedOut(CartEntity cart) {
        var items = cart.getItems().stream()
                .map(item -> new CartItem(
                        item.getMenuItemId(),
                        item.getName(),
                        item.getUnitPrice(),
                        item.getQuantity()
                ))
                .toList();

        BigDecimal total = cart.getTotal();

        return new CartCheckedOutV1(
                UUID.randomUUID(),
                Instant.now(),
                cart.getId(),
                cart.getCustomerId(),
                cart.getRestaurantId(),
                total,
                items
        );
    }

    public CartResponse createCartResponse(CartEntity cart) {
        var items = cart.getItems().stream()
                .map(item -> new CartItemResponse(
                        item.getId(),
                        item.getMenuItemId(),
                        item.getName(),
                        item.getUnitPrice(),
                        item.getQuantity()
                ))
                .toList();

        return new CartResponse(
                cart.getId(),
                cart.getRestaurantId(),
                cart.getTotal(),
                items
        );
    }
}
