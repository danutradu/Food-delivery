package com.food.delivery.order.util;

import com.food.delivery.order.dto.OrderItemResponse;
import com.food.delivery.order.dto.OrderResponse;
import com.food.delivery.order.model.OrderEntity;
import com.food.delivery.order.model.OrderItemEntity;
import fd.cart.CartCheckedOutV1;
import fd.cart.CartItem;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OrderFactory {

    public OrderEntity createOrderFromCart(CartCheckedOutV1 event) {
        var order = new OrderEntity();
        order.setCartId(event.getCartId());
        order.setCustomerId(event.getCustomerId());
        order.setRestaurantId(event.getRestaurantId());
        order.setTotal(event.getTotal());

        var items = event.getItems().stream()
                .map(item -> createOrderItemFromCart(item, order))
                .toList();
        order.setItems(items);

        return order;
    }

    private OrderItemEntity createOrderItemFromCart(CartItem cartItem, OrderEntity order) {
        var orderItem = new OrderItemEntity();
        orderItem.setMenuItemId(cartItem.getMenuItemId());
        orderItem.setName(cartItem.getName());
        orderItem.setUnitPrice(cartItem.getUnitPrice());
        orderItem.setQuantity(cartItem.getQuantity());
        orderItem.setOrder(order);
        return orderItem;
    }

    public OrderResponse createOrderResponse(OrderEntity order) {
        var items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getMenuItemId(),
                        item.getName(),
                        item.getUnitPrice(),
                        item.getQuantity()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getTotal(),
                order.getStatus(),
                items
        );
    }
}
