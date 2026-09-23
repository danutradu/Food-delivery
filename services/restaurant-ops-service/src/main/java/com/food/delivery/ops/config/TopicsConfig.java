package com.food.delivery.ops.config;

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
    public NewTopic restaurantAccepted() {
        return TopicBuilder.name(topics.getRestaurantAccepted()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic restaurantRejected() {
        return TopicBuilder.name(topics.getRestaurantRejected()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic orderReady() {
        return TopicBuilder.name(topics.getOrderReady()).partitions(partitions).replicas(replicas).build();
    }
}
