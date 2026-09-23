package com.food.delivery.ops.dto;

import com.food.delivery.ops.model.KitchenTicketEntity;
import com.food.delivery.ops.model.KitchenTicketStatus;

import java.time.Instant;
import java.util.UUID;

public record KitchenTicketResponse(
        UUID id,
        UUID orderId,
        UUID restaurantId,
        UUID customerId,
        KitchenTicketStatus status,
        String specialInstructions,
        int estimatedPrepTimeMinutes,
        Instant receivedAt,
        Instant updatedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant readyAt) {
    public static KitchenTicketResponse from(KitchenTicketEntity ticket) {
        return new KitchenTicketResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getRestaurantId(),
                ticket.getCustomerId(),
                ticket.getStatus(),
                ticket.getSpecialInstructions(),
                ticket.getEstimatedPrepTimeMinutes(),
                ticket.getReceivedAt(),
                ticket.getUpdatedAt(),
                ticket.getAcceptedAt(),
                ticket.getStartedAt(),
                ticket.getReadyAt()
        );
    }
}
