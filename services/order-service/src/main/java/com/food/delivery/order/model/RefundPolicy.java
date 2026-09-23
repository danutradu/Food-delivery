package com.food.delivery.order.model;

import java.math.BigDecimal;

public record RefundPolicy(
        boolean shouldRefund,
        RefundType refundType,
        BigDecimal fee,
        String reason
) {}
