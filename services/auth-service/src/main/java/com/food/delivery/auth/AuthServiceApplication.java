package com.food.delivery.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {"com.food.delivery.auth", "com.food.delivery.common.outbox", "com.food.delivery.common.config", "com.food.delivery.common.observability"})
@EnableJpaRepositories(basePackages = {"com.food.delivery.auth", "com.food.delivery.common.outbox"})
@EntityScan(basePackages = {"com.food.delivery.auth", "com.food.delivery.common.outbox"})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
