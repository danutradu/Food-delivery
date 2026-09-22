package com.food.delivery.common.config;

import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@RetryableTopic(
        attempts = "${kafka.retry.attempts:3}",
        backoff = @Backoff(
                delayExpression = "${kafka.retry.delay:1000}",
                multiplierExpression = "${kafka.retry.multiplier:2.0}",
                maxDelayExpression = "${kafka.retry.max-delay:10000}"
        ),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
)
public @interface StandardRetryableTopic {
}
