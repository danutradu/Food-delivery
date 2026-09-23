package com.food.delivery.payment.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class KafkaTopics {

    // Producer topics
    @Value("${kafka.topics.payment-authorized}")
    private String paymentAuthorized;

    @Value("${kafka.topics.payment-failed}")
    private String paymentFailed;

    @Value("${kafka.topics.refund-completed}")
    private String refundCompleted;

    @Value("${kafka.topics.fee-charged}")
    private String feeCharged;

    @Value("${kafka.topics.fee-failed}")
    private String feeFailed;
}
