package com.food.delivery.order.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class TopicsConfig {

    private final KafkaTopics topics;

    @Value("${kafka.topics.partitions}")
    private int partitions;

    @Value("${kafka.topics.replicas}")
    private int replicas;

    @Bean
    public NewTopic orderCreated() {
        return TopicBuilder.name(topics.getOrderCreated()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic paymentRequested() {
        return TopicBuilder.name(topics.getPaymentRequested()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic restaurantAcceptanceRequested() {
        return TopicBuilder.name(topics.getRestaurantAcceptanceRequested()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic deliveryRequested() {
        return TopicBuilder.name(topics.getDeliveryRequested()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic refundRequested() {
        return TopicBuilder.name(topics.getRefundRequested()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic orderCancelled() {
        return TopicBuilder.name(topics.getOrderCancelled()).partitions(partitions).replicas(replicas).build();
    }
}
