package com.food.delivery.common.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {

    @Mock
    OutboxRepository outboxRepository;

    @InjectMocks
    OutboxClaimService outboxClaimService;

    @Test
    void claimBatch_recoversStaleEventsAndClaimsPendingEvents() {
        ReflectionTestUtils.setField(outboxClaimService, "claimTimeoutSeconds", 300);
        ReflectionTestUtils.setField(outboxClaimService, "staleRecoveryBatchSize", 10);
        var staleEvent = event(OutboxStatus.IN_FLIGHT, Instant.now().minusSeconds(600));
        var recoveredEvent = event(OutboxStatus.PENDING, null);
        recoveredEvent.setId(staleEvent.getId());
        var pendingEvent = event(OutboxStatus.PENDING, null);
        when(outboxRepository.findStaleInFlightEvents(
                eq(OutboxStatus.IN_FLIGHT), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(staleEvent));
        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(recoveredEvent, pendingEvent));

        var claimed = outboxClaimService.claimBatch(10);

        assertThat(claimed).containsExactly(recoveredEvent, pendingEvent);
        assertThat(claimed).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
            assertThat(event.getClaimedAt()).isNotNull();
        });
        assertThat(recoveredEvent.getClaimedAt()).isEqualTo(pendingEvent.getClaimedAt());
        verify(outboxRepository).saveAll(List.of(staleEvent));
        verify(outboxRepository).saveAll(List.of(recoveredEvent, pendingEvent));
    }

    @Test
    void claimBatch_recoversEventsWithNullClaimedAt() {
        ReflectionTestUtils.setField(outboxClaimService, "claimTimeoutSeconds", 300);
        ReflectionTestUtils.setField(outboxClaimService, "staleRecoveryBatchSize", 10);
        var nullClaimedAtEvent = event(OutboxStatus.IN_FLIGHT, null);
        var pendingEvent = event(OutboxStatus.PENDING, null);
        when(outboxRepository.findStaleInFlightEvents(
                eq(OutboxStatus.IN_FLIGHT), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(nullClaimedAtEvent));
        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(nullClaimedAtEvent, pendingEvent));

        var claimed = outboxClaimService.claimBatch(10);

        assertThat(claimed).containsExactly(nullClaimedAtEvent, pendingEvent);
        assertThat(nullClaimedAtEvent.getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
        assertThat(nullClaimedAtEvent.getClaimedAt()).isNotNull();
    }

    private OutboxEventEntity event(OutboxStatus status, Instant claimedAt) {
        var event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setStatus(status);
        event.setClaimedAt(claimedAt);
        return event;
    }
}
