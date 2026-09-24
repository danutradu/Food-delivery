package com.food.delivery.auth.dto;

import com.food.delivery.auth.model.CourierApplicationEntity;
import com.food.delivery.auth.model.CourierApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record CourierApplicationResponse(
        UUID id,
        UUID userId,
        String fullName,
        String phoneNumber,
        String vehicleInformation,
        String operatingArea,
        CourierApplicationStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason) {

    public static CourierApplicationResponse from(CourierApplicationEntity application) {
        return new CourierApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.getFullName(),
                application.getPhoneNumber(),
                application.getVehicleInformation(),
                application.getOperatingArea(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getRejectionReason());
    }
}
