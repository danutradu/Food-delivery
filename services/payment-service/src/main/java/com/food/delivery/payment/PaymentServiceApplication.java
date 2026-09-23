package com.food.delivery.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.food.delivery.payment", "com.food.delivery.common"})
@EnableJpaRepositories(basePackages = {"com.food.delivery.payment", "com.food.delivery.common.outbox", "com.food.delivery.common.dlt"})
@EntityScan(basePackages = {"com.food.delivery.payment", "com.food.delivery.common.outbox", "com.food.delivery.common.dlt"})
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
