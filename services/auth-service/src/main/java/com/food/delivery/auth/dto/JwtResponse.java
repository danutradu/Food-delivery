package com.food.delivery.auth.dto;

public record JwtResponse(String accessToken, long expiresAtEpochSeconds, String tokenType) {}
