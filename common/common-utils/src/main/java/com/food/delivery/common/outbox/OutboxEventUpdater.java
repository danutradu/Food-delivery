package com.food.delivery.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventUpdater {

    private final OutboxRepository outboxRepository;

    @Transactional
    public void markPublished(UUID eventId, Instant claimedAt) {
        var event = findCurrentClaim(eventId, claimedAt);
        if (event == null) {
            return;
        }

        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setClaimedAt(null);
        event.setLastError(null);
        outboxRepository.save(event);
    }

    @Transactional
    public void markFailed(UUID eventId, Instant claimedAt, Throwable ex, int maxRetries) {
        var event = findCurrentClaim(eventId, claimedAt);
        if (event == null) {
            return;
        }

        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastRetryAt(Instant.now());
        event.setLastError(errorDescription(ex));

        if (event.getRetryCount() >= maxRetries) {
            event.setStatus(OutboxStatus.PARKED);
            log.error("Max retries exceeded for outbox event id={}, PARKED for manual intervention", event.getId(), ex);
        } else {
            event.setStatus(OutboxStatus.PENDING);
        }
        event.setClaimedAt(null);
        outboxRepository.save(event);
    }

    private String errorDescription(Throwable ex) {
        return ex.getClass().getName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
    }

    private OutboxEventEntity findCurrentClaim(UUID eventId, Instant claimedAt) {
        var event = outboxRepository.findByIdAndStatus(eventId, OutboxStatus.IN_FLIGHT).orElse(null);
        if (event == null || claimedAt == null || event.getClaimedAt() == null
                || claimedAt.toEpochMilli() != event.getClaimedAt().toEpochMilli()) {
            log.warn("Ignoring stale outbox completion id={} claimedAt={}", eventId, claimedAt);
            return null;
        }
        return event;
    }
}
