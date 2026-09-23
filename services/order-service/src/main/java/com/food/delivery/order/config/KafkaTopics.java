package com.food.delivery.order.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class KafkaTopics {

    // Consumer topics
    @Value("${kafka.topics.cart-checked-out}")
    private String cartCheckedOut;

    // Producer topics
    @Value("${kafka.topics.order-created}")
    private String orderCreated;

    @Value("${kafka.topics.payment-requested}")
    private String paymentRequested;

    @Value("${kafka.topics.restaurant-acceptance-requested}")
    private String restaurantAcceptanceRequested;

    @Value("${kafka.topics.delivery-requested}")
    private String deliveryRequested;

    @Value("${kafka.topics.refund-requested}")
    private String refundRequested;

    @Value("${kafka.topics.fee-requested}")
    private String feeRequested;

    @Value("${kafka.topics.order-cancelled}")
    private String orderCancelled;
}
