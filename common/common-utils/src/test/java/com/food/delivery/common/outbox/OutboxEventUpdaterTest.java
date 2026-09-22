package com.food.delivery.common.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventUpdaterTest {

    @Mock
    OutboxRepository outboxRepository;
    @InjectMocks
    OutboxEventUpdater outboxEventUpdater;

    @Test
    void markPublished_currentClaim_updatesManagedEvent() {
        var claimedAt = Instant.now();
        var event = claimedEvent(claimedAt);
        when(outboxRepository.findByIdAndStatus(event.getId(), OutboxStatus.IN_FLIGHT))
                .thenReturn(Optional.of(event));

        outboxEventUpdater.markPublished(event.getId(), claimedAt);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getClaimedAt()).isNull();
    }

    @Test
    void markPublished_acceptsDatabasePrecisionClaim() {
        var claimedAt = Instant.ofEpochSecond(1_700_000_000L, 123_456_789);
        var event = claimedEvent(Instant.ofEpochMilli(claimedAt.toEpochMilli()));
        when(outboxRepository.findByIdAndStatus(event.getId(), OutboxStatus.IN_FLIGHT))
                .thenReturn(Optional.of(event));

        outboxEventUpdater.markPublished(event.getId(), claimedAt);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(outboxRepository).save(event);
    }

    @Test
    void markFailed_belowRetryLimit_returnsEventToPending() {
        var claimedAt = Instant.now();
        var event = claimedEvent(claimedAt);
        event.setRetryCount(1);
        when(outboxRepository.findByIdAndStatus(event.getId(), OutboxStatus.IN_FLIGHT))
                .thenReturn(Optional.of(event));

        outboxEventUpdater.markFailed(event.getId(), claimedAt, new RuntimeException("Kafka unavailable"), 3);

        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getClaimedAt()).isNull();
        assertThat(event.getLastError()).isEqualTo("java.lang.RuntimeException: Kafka unavailable");
        verify(outboxRepository).save(event);
    }

    @Test
    void markFailed_atRetryLimit_parksEvent() {
        var claimedAt = Instant.now();
        var event = claimedEvent(claimedAt);
        event.setRetryCount(2);
        when(outboxRepository.findByIdAndStatus(event.getId(), OutboxStatus.IN_FLIGHT))
                .thenReturn(Optional.of(event));

        outboxEventUpdater.markFailed(event.getId(), claimedAt, new RuntimeException("Kafka unavailable"), 3);

        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PARKED);
        assertThat(event.getClaimedAt()).isNull();
    }

    @Test
    void staleCompletion_doesNotOverwriteCurrentClaim() {
        var staleClaim = Instant.now().minusSeconds(600);
        var event = claimedEvent(Instant.now());
        when(outboxRepository.findByIdAndStatus(event.getId(), OutboxStatus.IN_FLIGHT))
                .thenReturn(Optional.of(event));

        outboxEventUpdater.markPublished(event.getId(), staleClaim);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
        verify(outboxRepository, never()).save(event);
    }

    private OutboxEventEntity claimedEvent(Instant claimedAt) {
        var event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setStatus(OutboxStatus.IN_FLIGHT);
        event.setClaimedAt(claimedAt);
        return event;
    }
}
