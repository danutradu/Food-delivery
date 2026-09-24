package com.food.delivery.delivery.config;

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
    public NewTopic courierRoleGranted() {
        return TopicBuilder.name(topics.getCourierRoleGranted()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic courierRoleRevoked() {
        return TopicBuilder.name(topics.getCourierRoleRevoked()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic courierAssigned() {
        return TopicBuilder.name(topics.getCourierAssigned()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic pickedUp() {
        return TopicBuilder.name(topics.getOrderPickedUp()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic delivered() {
        return TopicBuilder.name(topics.getOrderDelivered()).partitions(partitions).replicas(replicas).build();
    }
}
