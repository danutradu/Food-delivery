package com.food.delivery.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CourierDisableRequest(@NotBlank String reason) {}
