package com.food.delivery.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemUpsert(UUID sectionId, @NotBlank String name, String description,
                             @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
                             boolean available, int version) {
}
