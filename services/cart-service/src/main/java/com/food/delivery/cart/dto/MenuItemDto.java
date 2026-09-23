package com.food.delivery.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemDto(UUID id, String name, BigDecimal price, boolean available) {}
