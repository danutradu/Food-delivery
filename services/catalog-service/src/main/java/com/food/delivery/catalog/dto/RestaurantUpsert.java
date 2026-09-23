package com.food.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RestaurantUpsert(@NotNull UUID ownerUserId, @NotBlank String name, @NotBlank String address, boolean isOpen) {}
