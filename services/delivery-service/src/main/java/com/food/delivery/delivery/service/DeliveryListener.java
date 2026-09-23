package com.food.delivery.delivery.service;

import com.food.delivery.common.config.StandardRetryableTopic;
import com.food.delivery.common.dlt.DltEventService;
import fd.delivery.DeliveryRequestedV1;
import fd.order.OrderCancelledV1;
import fd.restaurant.OrderReadyForPickupV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryListener {

    private final DeliveryService deliveryService;
    private final AssignmentService assignmentService;
    private final DltEventService dltEventService;

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.delivery-requested}", groupId = "${kafka.consumer.group-id}")
    public void onDeliveryRequested(DeliveryRequestedV1 event) {
        deliveryService.processDeliveryRequest(event);
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-cancelled}", groupId = "${kafka.consumer.group-id}")
    public void onOrderCancelled(OrderCancelledV1 event) {
        deliveryService.cancelDelivery(event.getOrderId(), event.getReason());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-ready}", groupId = "${kafka.consumer.group-id}")
    public void onOrderReadyForPickup(OrderReadyForPickupV1 event) {
        deliveryService.markReadyForPickup(event.getOrderId());
    }

    @DltHandler
    public void handleDlt(Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String originalKey,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset) {
        dltEventService.park("delivery-service", topic, originalTopic, originalKey,
                originalPartition, originalOffset, event);
    }
}
