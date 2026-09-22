package com.food.delivery.auth.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class KafkaTopics {

    // Producer topics
    @Value("${kafka.topics.user-registered}")
    private String userRegistered;
}
