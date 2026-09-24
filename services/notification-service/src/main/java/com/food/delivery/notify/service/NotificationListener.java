package com.food.delivery.notify.service;

import com.food.delivery.common.config.StandardRetryableTopic;
import fd.delivery.CourierAssignedV1;
import fd.delivery.OrderDeliveredV1;
import fd.delivery.OrderPickedUpV1;
import fd.order.OrderCancelledV1;
import fd.order.OrderCreatedV1;
import fd.payment.*;
import fd.restaurant.RestaurantAcceptedV1;
import fd.restaurant.RestaurantRejectedV1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationListener {

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-created}", groupId = "${kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedV1 e) {
        log.info("Notify: Order created {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.payment-authorized}", groupId = "${kafka.consumer.group-id}")
    public void onPaymentAuth(PaymentAuthorizedV1 e) {
        log.info("Notify: Payment authorized {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.restaurant-accepted}", groupId = "${kafka.consumer.group-id}")
    public void onRestaurantAccepted(RestaurantAcceptedV1 e) {
        log.info("Notify: Restaurant accepted {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.restaurant-rejected}", groupId = "${kafka.consumer.group-id}")
    public void onRestaurantRejected(RestaurantRejectedV1 e) {
        log.info("Notify: Restaurant rejected {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.courier-assigned}", groupId = "${kafka.consumer.group-id}")
    public void onCourierAssigned(CourierAssignedV1 e) {
        log.info("Notify: Courier assigned {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-picked-up}", groupId = "${kafka.consumer.group-id}")
    public void onPickedUp(OrderPickedUpV1 e) {
        log.info("Notify: Order picked up {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-delivered}", groupId = "${kafka.consumer.group-id}")
    public void onDelivered(OrderDeliveredV1 e) {
        log.info("Notify: Order delivered {}", e.getOrderId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-cancelled}", groupId = "${kafka.consumer.group-id}")
    public void onOrderCancelled(OrderCancelledV1 e) {
        log.info("Notify: Order cancelled {} reason{}", e.getOrderId(), e.getReason());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.payment-failed}", groupId = "${kafka.consumer.group-id}")
    public void onPaymentFailed(PaymentFailedV1 e) {
        log.info("Notify: Payment failed {} reason {}", e.getOrderId(), e.getFailureReason());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.refund-completed}", groupId = "${kafka.consumer.group-id}")
    public void onRefundCompleted(RefundCompletedV1 e) {
        log.info("Notify: Refund completed {} amount={} reason={}", e.getOrderId(), e.getAmount(), e.getReason());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.fee-charged}", groupId = "${kafka.consumer.group-id}")
    public void onFeeCharged(FeeChargedV1 e) {
        log.info("Notify: Fee charged successfully {} amount={} reason={}", e.getOrderId(), e.getFee(), e.getReason());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.fee-failed}", groupId = "${kafka.consumer.group-id}")
    public void onFeeFailed(FeeFailedV1 e) {
        log.info("Notify: Fee charging failed {} amount={} reason={}", e.getOrderId(), e.getFee(), e.getReason());
    }
}
