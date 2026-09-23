package com.food.delivery.order.util;

import com.food.delivery.order.model.OrderEntity;
import com.food.delivery.order.model.RefundPolicy;
import com.food.delivery.order.model.RefundType;
import fd.order.OrderCancelledV1;
import fd.order.OrderCreatedV1;
import fd.order.OrderItem;
import fd.payment.FeeRequestedV1;
import fd.payment.PaymentRequestedV1;
import fd.payment.RefundRequestedV1;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class OrderEventFactory {

    public OrderCreatedV1 createOrderCreated(OrderEntity order) {
        var items = order.getItems().stream()
                .map(item -> new OrderItem(
                        item.getMenuItemId(),
                        item.getName(),
                        item.getUnitPrice(),
                        item.getQuantity()
                ))
                .toList();

        return OrderCreatedV1.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOccurredAt(Instant.now())
                .setOrderId(order.getId())
                .setCustomerId(order.getCustomerId())
                .setRestaurantId(order.getRestaurantId())
                .setItems(items)
                .setTotal(order.getTotal())
                .build();
    }

    public PaymentRequestedV1 createPaymentRequested(OrderEntity order) {
        return new PaymentRequestedV1(
                UUID.randomUUID(),
                Instant.now(),
                order.getId(),
                order.getTotal()
        );
    }

    public RefundRequestedV1 createRefundRequested(OrderEntity order, RefundPolicy refundPolicy) {
        BigDecimal refundAmount = refundPolicy.refundType() == RefundType.FULL ? order.getTotal() : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

        return new RefundRequestedV1(
                UUID.randomUUID(),
                Instant.now(),
                order.getId(),
                refundAmount,
                refundPolicy.reason()
        );
    }

    public FeeRequestedV1 createFeeRequested(OrderEntity order, RefundPolicy refundPolicy) {
        return new FeeRequestedV1(
                UUID.randomUUID(),
                Instant.now(),
                order.getId(),
                refundPolicy.fee(),
                refundPolicy.reason()
        );
    }

    public OrderCancelledV1 createOrderCancelled(OrderEntity order, String reason) {
        return new OrderCancelledV1(
                UUID.randomUUID(),
                Instant.now(),
                order.getId(),
                order.getCustomerId(),
                reason
        );
    }
}
