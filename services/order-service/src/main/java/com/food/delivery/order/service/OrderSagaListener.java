package com.food.delivery.order.service;

import com.food.delivery.common.config.StandardRetryableTopic;
import com.food.delivery.common.dlt.DltEventService;
import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.order.config.KafkaTopics;
import com.food.delivery.order.model.OrderStatus;
import com.food.delivery.order.repository.OrderRepository;
import fd.cart.CartCheckedOutV1;
import fd.delivery.OrderDeliveredV1;
import fd.delivery.OrderPickedUpV1;
import fd.order.OrderCancelledV1;
import fd.payment.*;
import fd.restaurant.OrderReadyForPickupV1;
import fd.restaurant.RestaurantAcceptedV1;
import fd.restaurant.RestaurantRejectedV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaListener {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;
    private final OrderService orderService;
    private final DltEventService dltEventService;

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.cart-checked-out}", groupId = "${kafka.consumer.group-id}")
    public void onCartCheckedOut(CartCheckedOutV1 event) {
        orderService.createOrderFromCart(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.payment-authorized}", groupId = "${kafka.consumer.group-id}")
    public void onPaymentAuthorized(PaymentAuthorizedV1 event) {
        orderService.processPaymentAuthorized(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.restaurant-accepted}", groupId = "${kafka.consumer.group-id}")
    public void onRestaurantAccepted(RestaurantAcceptedV1 event) {
        orderService.processRestaurantAccepted(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.payment-failed}", groupId = "${kafka.consumer.group-id}")
    public void onPaymentFailed(PaymentFailedV1 event) {
        orderService.processPaymentFailed(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.restaurant-rejected}", groupId = "${kafka.consumer.group-id}")
    public void onRestaurantRejected(RestaurantRejectedV1 event) {
        orderService.processRestaurantRejected(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-ready}", groupId = "${kafka.consumer.group-id}")
    public void onOrderReady(OrderReadyForPickupV1 event) {
        orderService.processOrderReady(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-picked-up}", groupId = "${kafka.consumer.group-id}")
    public void onOrderPickedUp(OrderPickedUpV1 event) {
        orderService.processOrderPickedUp(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-delivered}", groupId = "${kafka.consumer.group-id}")
    public void onOrderDelivered(OrderDeliveredV1 event) {
        orderService.processOrderDelivered(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.fee-charged}", groupId = "${kafka.consumer.group-id}")
    public void onFeeCharged(FeeChargedV1 event) {
        orderService.processFeeCharged(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.fee-failed}", groupId = "${kafka.consumer.group-id}")
    public void onFeeFailed(FeeFailedV1 event) {
        orderService.processFeeFailed(event);
    }

    @DltHandler
    @Transactional
    public void handleDlt(Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String originalKey,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset) {
        var dltEventId = dltEventService.park("order-service", topic, originalTopic, originalKey,
                originalPartition, originalOffset, event);
        if (event instanceof PaymentAuthorizedV1 paymentAuthorized) {
            handleDltPaymentAuthorized(paymentAuthorized, topic);
            dltEventService.markResolved(dltEventId);
        } else if (event instanceof RestaurantAcceptedV1 restaurantAccepted) {
            handleDltRestaurantAccepted(restaurantAccepted, topic);
            dltEventService.markResolved(dltEventId);
        } else {
            log.error("Unhandled order event in DLT eventType={} topic={}",
                    event == null ? "null" : event.getClass().getName(), topic);
        }
    }

    private void handleDltPaymentAuthorized(PaymentAuthorizedV1 event, String topic) {
        log.error("Payment authorized event failed after all retries, requesting refund orderId={} topic={}", event.getOrderId(), topic);

        var order = orderService.findOrder(event.getOrderId());

        var refundEvent = new RefundRequestedV1(
                UUID.randomUUID(),
                Instant.now(),
                event.getOrderId(),
                event.getAmount(),
                "Order processing failed after payment authorization"
        );
        outboxService.publish(topics.getRefundRequested(), event.getOrderId().toString(), refundEvent);

        order.setStatus(OrderStatus.PROCESSING_FAILED);
        orderRepository.save(order);

        log.info("Refund requested for failed order processing orderId={}", event.getOrderId());
    }

    private void handleDltRestaurantAccepted(RestaurantAcceptedV1 event, String topic) {
        log.error("Restaurant accepted event failed after all retries, cancelling order and requesting refund orderId={} topic={}", event.getOrderId(), topic);

        var order = orderService.findOrder(event.getOrderId());

        var cancelEvent = new OrderCancelledV1(
                UUID.randomUUID(),
                Instant.now(),
                event.getOrderId(),
                order.getCustomerId(),
                "Order processing failed after restaurant acceptance"
        );

        var refundEvent = new RefundRequestedV1(
                UUID.randomUUID(),
                Instant.now(),
                event.getOrderId(),
                order.getTotal(),
                "Order processing failed after restaurant acceptance"
        );

        outboxService.publish(topics.getOrderCancelled(), event.getOrderId().toString(), cancelEvent);
        outboxService.publish(topics.getRefundRequested(), event.getOrderId().toString(), refundEvent);

        order.setStatus(OrderStatus.PROCESSING_FAILED);
        orderRepository.save(order);

        log.info("Cancellation and refund requested for failed order orderId={}", event.getOrderId());
    }
}
