package com.food.delivery.common.outbox;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    PARKED,
    PUBLISHED
}
