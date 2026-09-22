package com.food.delivery.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxRepository outboxRepository;

    @Value("${outbox.claim-timeout-seconds:300}")
    private int claimTimeoutSeconds;

    @Value("${outbox.stale-recovery-batch-size:50}")
    private int staleRecoveryBatchSize;

    @Transactional
    public List<OutboxEventEntity> claimBatch(int batchSize) {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var stalePageable = PageRequest.of(0, staleRecoveryBatchSize, Sort.by("createdAt"));
        var pendingPageable = PageRequest.of(0, batchSize, Sort.by("createdAt"));
        var staleCutoff = now.minus(claimTimeoutSeconds, ChronoUnit.SECONDS);

        var staleEvents = outboxRepository.findStaleInFlightEvents(
                OutboxStatus.IN_FLIGHT, staleCutoff, stalePageable);
        staleEvents.forEach(event -> {
            event.setStatus(OutboxStatus.PENDING);
            event.setClaimedAt(null);
        });
        outboxRepository.saveAll(staleEvents);

        var events = outboxRepository.findByStatus(OutboxStatus.PENDING, pendingPageable);
        events.forEach(event -> {
            event.setStatus(OutboxStatus.IN_FLIGHT);
            event.setClaimedAt(now);
        });
        outboxRepository.saveAll(events);
        return events;
    }
}
