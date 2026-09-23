package com.food.delivery.delivery.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class KafkaTopics {

    @Value("${kafka.topics.courier-assigned}")
    private String courierAssigned;

    @Value("${kafka.topics.order-picked-up}")
    private String orderPickedUp;

    @Value("${kafka.topics.order-delivered}")
    private String orderDelivered;
}
