package com.food.delivery.common.dlt;

import fd.payment.PaymentAuthorizedV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DltEventServiceTest {

    @Mock
    DltEventRepository repository;

    @Test
    void park_avroEvent_persistsReplayMetadataAndPayload() {
        var event = paymentAuthorizedEvent();
        var service = new DltEventService(repository);

        var id = service.park("payment-service", "payment.events.DLT", "payment.events",
                "order-123", 2, 45L, event);

        var captor = ArgumentCaptor.forClass(DltEventEntity.class);
        verify(repository).save(captor.capture());
        var record = captor.getValue();

        assertThat(id).isEqualTo(record.getId());
        assertThat(record.getServiceName()).isEqualTo("payment-service");
        assertThat(record.getSourceTopic()).isEqualTo("payment.events");
        assertThat(record.getDltTopic()).isEqualTo("payment.events.DLT");
        assertThat(record.getOriginalKey()).isEqualTo("order-123");
        assertThat(record.getOriginalPartition()).isEqualTo(2);
        assertThat(record.getOriginalOffset()).isEqualTo(45L);
        assertThat(record.getEventType()).isEqualTo(event.getClass().getName());
        assertThat(record.getPayloadBytes()).isNotEmpty();
        assertThat(record.getPayloadEncoding()).isEqualTo("AVRO_BINARY");
        assertThat(record.getSchemaFullName()).isEqualTo(event.getSchema().getFullName());
        assertThat(record.getStatus()).isEqualTo(DltEventStatus.PARKED);
        assertThat(record.getParkedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void park_missingSourceTopic_rejectsWithoutPersisting() {
        var service = new DltEventService(repository);

        assertThatThrownBy(() -> service.park("payment-service", "payment.events.DLT", null,
                "order-123", 2, 45L, paymentAuthorizedEvent()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Original source topic is required for DLT replay");

        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void park_nonAvroEvent_rejectsWithoutPersisting() {
        var service = new DltEventService(repository);

        assertThatThrownBy(() -> service.park("payment-service", "payment.events.DLT", "payment.events",
                "order-123", 2, 45L, "not-avro"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    private PaymentAuthorizedV1 paymentAuthorizedEvent() {
        return new PaymentAuthorizedV1(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), new BigDecimal("12.00"), "AUTH-123");
    }
}
