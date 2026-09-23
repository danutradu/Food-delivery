package com.food.delivery.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID menuItemId, String name, BigDecimal unitPrice, int quantity) {}
