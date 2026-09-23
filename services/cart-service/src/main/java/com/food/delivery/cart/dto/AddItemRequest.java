package com.food.delivery.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddItemRequest(@NotNull UUID restaurantId, @NotNull UUID menuItemId, @Min(1) int quantity) {}
