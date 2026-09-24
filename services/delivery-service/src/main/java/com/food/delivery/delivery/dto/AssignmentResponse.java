package com.food.delivery.delivery.dto;

import com.food.delivery.delivery.model.AssignmentStatus;
import com.food.delivery.delivery.model.CourierAssignmentEntity;
import com.food.delivery.delivery.model.DeliveryEntity;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID assignmentId,
        UUID orderId,
        UUID courierId,
        AssignmentStatus status,
        Instant offeredAt,
        Instant expiresAt,
        Instant respondedAt,
        Instant completedAt
) {
    public static AssignmentResponse from(CourierAssignmentEntity assignment, DeliveryEntity delivery) {
        return new AssignmentResponse(
                assignment.getId(),
                delivery.getOrderId(),
                assignment.getCourierId(),
                assignment.getStatus(),
                assignment.getOfferedAt(),
                assignment.getExpiresAt(),
                assignment.getRespondedAt(),
                assignment.getCompletedAt()
        );
    }
}
