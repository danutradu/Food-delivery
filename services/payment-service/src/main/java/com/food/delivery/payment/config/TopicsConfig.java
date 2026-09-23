package com.food.delivery.payment.config;

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
    public NewTopic paymentAuthorized() {
        return TopicBuilder.name(topics.getPaymentAuthorized()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic paymentFailed() {
        return TopicBuilder.name(topics.getPaymentFailed()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic refundCompleted() {
        return TopicBuilder.name(topics.getRefundCompleted()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic feeCharged() {
        return TopicBuilder.name(topics.getFeeCharged()).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic feeFailed() {
        return TopicBuilder.name(topics.getFeeFailed()).partitions(partitions).replicas(replicas).build();
    }
}
