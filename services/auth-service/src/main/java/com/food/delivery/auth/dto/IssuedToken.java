package com.food.delivery.auth.dto;

public record IssuedToken(String token, long expiresAtEpochSeconds) {}
