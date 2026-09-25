package com.food.delivery.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MenuSectionUpsert(
        @NotBlank String name,
        @Min(0) int displayOrder) {}
