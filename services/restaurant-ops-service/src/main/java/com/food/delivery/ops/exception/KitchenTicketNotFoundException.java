package com.food.delivery.ops.exception;

import java.util.UUID;

public class KitchenTicketNotFoundException extends RuntimeException {
    public KitchenTicketNotFoundException(UUID orderId) {
        super("Kitchen ticket not found for order: " + orderId);
    }
}
