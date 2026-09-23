package com.food.delivery.ops.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class KafkaTopics {

    // Producer topics
    @Value("${kafka.topics.restaurant-accepted}")
    private String restaurantAccepted;

    @Value("${kafka.topics.restaurant-rejected}")
    private String restaurantRejected;

    @Value("${kafka.topics.order-ready}")
    private String orderReady;
}
