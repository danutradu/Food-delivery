package com.food.delivery.common.observability;

import com.food.delivery.common.dlt.DltEventRepository;
import com.food.delivery.common.dlt.DltEventStatus;
import com.food.delivery.common.outbox.OutboxRepository;
import com.food.delivery.common.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean({OutboxRepository.class, DltEventRepository.class})
@RequiredArgsConstructor
public class MessagingMetrics implements MeterBinder {

    private final OutboxRepository outboxRepository;
    private final DltEventRepository dltEventRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        registerOutboxGauge(registry, OutboxStatus.PENDING);
        registerOutboxGauge(registry, OutboxStatus.IN_FLIGHT);
        registerOutboxGauge(registry, OutboxStatus.PUBLISHED);
        registerOutboxGauge(registry, OutboxStatus.PARKED);
        registerDltGauge(registry, DltEventStatus.PARKED);
        registerDltGauge(registry, DltEventStatus.REPLAYED);
        registerDltGauge(registry, DltEventStatus.RESOLVED);
    }

    private void registerOutboxGauge(MeterRegistry registry, OutboxStatus status) {
        Gauge.builder("food_delivery_outbox_events", outboxRepository,
                        repository -> repository.countByStatus(status))
                .description("Number of outbox events by processing status")
                .tag("status", status.name())
                .register(registry);
    }

    private void registerDltGauge(MeterRegistry registry, DltEventStatus status) {
        Gauge.builder("food_delivery_dlt_events", dltEventRepository,
                        repository -> repository.countByStatus(status))
                .description("Number of DLT events by processing status")
                .tag("status", status.name())
                .register(registry);
    }
}
