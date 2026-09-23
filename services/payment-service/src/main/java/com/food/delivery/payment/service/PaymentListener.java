package com.food.delivery.payment.service;

import com.food.delivery.common.config.StandardRetryableTopic;
import com.food.delivery.common.dlt.DltEventService;
import fd.payment.FeeRequestedV1;
import fd.payment.PaymentRequestedV1;
import fd.payment.RefundRequestedV1;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;
    private final DltEventService dltEventService;

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.payment-requested}", groupId = "${kafka.consumer.group-id}")
    public void onPaymentRequested(PaymentRequestedV1 event) {
        paymentService.processPaymentRequest(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.refund-requested}", groupId = "${kafka.consumer.group-id}")
    public void onRefundRequested(RefundRequestedV1 event) {
        paymentService.processRefundRequest(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.fee-requested}", groupId = "${kafka.consumer.group-id}")
    public void onFeeRequested(FeeRequestedV1 event) {
        paymentService.processFeeRequest(event);
    }

    @DltHandler
    public void handleDlt(Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String originalKey,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset) {
        dltEventService.park("payment-service", topic, originalTopic, originalKey,
                originalPartition, originalOffset, event);
    }
}
