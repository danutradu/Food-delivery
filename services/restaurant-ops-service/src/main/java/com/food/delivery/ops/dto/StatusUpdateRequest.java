package com.food.delivery.ops.dto;

import com.food.delivery.ops.model.KitchenTicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StatusUpdateRequest(
        @NotNull KitchenTicketStatus status,
        @Positive Integer etaMinutes,
        String reason
) {}
