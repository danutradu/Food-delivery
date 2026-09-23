package com.food.delivery.order.dto;

import com.food.delivery.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID orderId, BigDecimal total, OrderStatus status, List<OrderItemResponse> items) {}
