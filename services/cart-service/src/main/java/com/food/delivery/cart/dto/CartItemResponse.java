package com.food.delivery.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(UUID itemId, UUID menuItemId, String name, BigDecimal unitPrice, int quantity) {}
