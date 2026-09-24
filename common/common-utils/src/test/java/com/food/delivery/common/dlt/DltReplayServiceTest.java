package com.food.delivery.common.dlt;

import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DltReplayServiceTest {

    @Mock
    DltEventRepository repository;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    DltEventPayloadDecoder payloadDecoder;

    @Mock
    SpecificRecord event;

    @Test
    void replay_parkedEvent_publishesAndMarksReplayed() {
        var record = parkedEvent();
        when(repository.findByIdForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(payloadDecoder.decode(record)).thenReturn(event);
        when(kafkaTemplate.send(record.getSourceTopic(), record.getOriginalKey(), event))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.save(record)).thenReturn(record);

        var result = new DltReplayService(repository, kafkaTemplate, payloadDecoder).replay(record.getId());

        assertThat(result).isSameAs(record);
        assertThat(record.getStatus()).isEqualTo(DltEventStatus.REPLAYED);
        verify(kafkaTemplate).send(record.getSourceTopic(), record.getOriginalKey(), event);
        verify(repository).save(record);
    }

    @Test
    void replay_nonParkedEvent_rejectsWithoutPublishing() {
        var record = parkedEvent();
        record.setStatus(DltEventStatus.REPLAYED);
        when(repository.findByIdForUpdate(record.getId())).thenReturn(Optional.of(record));

        var service = new DltReplayService(repository, kafkaTemplate, payloadDecoder);

        assertThatThrownBy(() -> service.replay(record.getId()))
                .isInstanceOf(DltReplayService.DltEventStateException.class);
        verify(payloadDecoder, never()).decode(record);
        verify(kafkaTemplate, never()).send(eq(record.getSourceTopic()), eq(record.getOriginalKey()), eq(event));
    }

    @Test
    void replay_missingEvent_throwsNotFound() {
        var id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        var service = new DltReplayService(repository, kafkaTemplate, payloadDecoder);

        assertThatThrownBy(() -> service.replay(id))
                .isInstanceOf(DltReplayService.DltEventNotFoundException.class);
    }

    @Test
    void replay_decodeFailure_keepsEventParked() {
        var record = parkedEvent();
        when(repository.findByIdForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(payloadDecoder.decode(record)).thenThrow(
                new DltEventPayloadDecoder.DltEventPayloadException(record.getId(), new IllegalArgumentException()));

        var service = new DltReplayService(repository, kafkaTemplate, payloadDecoder);

        assertThatThrownBy(() -> service.replay(record.getId()))
                .isInstanceOf(DltReplayService.DltReplayException.class);
        assertThat(record.getStatus()).isEqualTo(DltEventStatus.PARKED);
        verify(kafkaTemplate, never()).send(eq(record.getSourceTopic()), eq(record.getOriginalKey()), eq(event));
        verify(repository, never()).save(record);
    }

    @Test
    void replay_publishFailure_keepsEventParked() {
        var record = parkedEvent();
        when(repository.findByIdForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(payloadDecoder.decode(record)).thenReturn(event);
        when(kafkaTemplate.send(record.getSourceTopic(), record.getOriginalKey(), event))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        var service = new DltReplayService(repository, kafkaTemplate, payloadDecoder);

        assertThatThrownBy(() -> service.replay(record.getId()))
                .isInstanceOf(DltReplayService.DltReplayException.class);
        assertThat(record.getStatus()).isEqualTo(DltEventStatus.PARKED);
        verify(repository, never()).save(record);
    }

    private DltEventEntity parkedEvent() {
        var record = new DltEventEntity();
        record.setId(UUID.randomUUID());
        record.setSourceTopic("order.events");
        record.setOriginalKey("order-123");
        record.setStatus(DltEventStatus.PARKED);
        return record;
    }
}
