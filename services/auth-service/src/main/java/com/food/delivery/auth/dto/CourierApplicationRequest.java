package com.food.delivery.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CourierApplicationRequest(
        @NotBlank String fullName,
        @NotBlank String phoneNumber,
        @NotBlank String vehicleInformation,
        @NotBlank String operatingArea) {}
