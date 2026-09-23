package com.food.delivery.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID cartId,
        UUID restaurantId,
        BigDecimal total,
        List<CartItemResponse> items
) {}
