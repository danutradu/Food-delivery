package com.food.delivery.ops.service;

import com.food.delivery.common.config.StandardRetryableTopic;
import com.food.delivery.common.dlt.DltEventService;
import fd.order.OrderCancelledV1;
import fd.restaurant.RestaurantAcceptanceRequestedV1;
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
public class OpsListener {
    private final OpsService opsService;
    private final DltEventService dltEventService;

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.acceptance-requested}", groupId = "${kafka.consumer.group-id}")
    public void onAcceptanceRequested(RestaurantAcceptanceRequestedV1 event) {
        opsService.processAcceptanceRequest(event.getOrderId(), event.getRestaurantId(), event.getCustomerId());
    }

    @StandardRetryableTopic
    @KafkaListener(topics = "${kafka.topics.order-cancelled}", groupId = "${kafka.consumer.group-id}")
    public void onOrderCancelled(OrderCancelledV1 event) {
        opsService.cancelKitchenTicket(event.getOrderId(), event.getReason());
    }

    @DltHandler
    public void handleDlt(Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String originalKey,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
                          @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset) {
        dltEventService.park("restaurant-ops-service", topic, originalTopic, originalKey,
                originalPartition, originalOffset, event);
    }
}
