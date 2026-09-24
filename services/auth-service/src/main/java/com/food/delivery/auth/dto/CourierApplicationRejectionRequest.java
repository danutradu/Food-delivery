package com.food.delivery.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CourierApplicationRejectionRequest(@NotBlank String reason, boolean permanent) {}
