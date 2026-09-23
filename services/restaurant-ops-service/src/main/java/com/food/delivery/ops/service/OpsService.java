package com.food.delivery.ops.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.ops.config.KafkaTopics;
import com.food.delivery.ops.dto.KitchenTicketResponse;
import com.food.delivery.ops.dto.StatusUpdateRequest;
import com.food.delivery.ops.exception.KitchenTicketNotFoundException;
import com.food.delivery.ops.model.KitchenTicketEntity;
import com.food.delivery.ops.model.KitchenTicketStatus;
import com.food.delivery.ops.repository.KitchenTicketRepository;
import com.food.delivery.ops.util.OpsEventFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpsService {
    private final KitchenTicketRepository ticketRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    public void processAcceptanceRequest(UUID orderId, UUID restaurantId, UUID customerId) {
        log.info("Process acceptance request orderId={} restaurantId={}", orderId, restaurantId);

        if (ticketRepository.findByOrderId(orderId).isPresent()) {
            log.debug("Kitchen ticket already exists orderId={}", orderId);
            return;
        }

        var ticket = new KitchenTicketEntity();
        ticket.setOrderId(orderId);
        ticket.setRestaurantId(restaurantId);
        ticket.setCustomerId(customerId);
        ticket.setStatus(KitchenTicketStatus.PENDING);
        ticket.setReceivedAt(Instant.now());
        ticketRepository.save(ticket);
    }

    @Transactional
    public KitchenTicketResponse updateStatus(UUID orderId, StatusUpdateRequest request) {
        log.info("Updating status orderId={} status={}", orderId, request.status());

        var ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new KitchenTicketNotFoundException(orderId));

        validateTransition(ticket.getStatus(), request.status());

        switch (request.status()) {
            case ACCEPTED -> {
                ticket.setStatus(KitchenTicketStatus.ACCEPTED);
                ticket.setAcceptedAt(Instant.now());
                if (request.etaMinutes() != null) {
                    ticket.setEstimatedPrepTimeMinutes(request.etaMinutes());
                }
                var acceptedEvent = OpsEventFactory.createRestaurantAccepted(ticket, request.etaMinutes() != null ? request.etaMinutes() : 15);
                outboxService.publish(topics.getRestaurantAccepted(), acceptedEvent.getOrderId().toString(), acceptedEvent);
            }
            case REJECTED -> {
                ticket.setStatus(KitchenTicketStatus.REJECTED);
                var rejectedEvent = OpsEventFactory.createRestaurantRejected(ticket, request.reason() != null ? request.reason() : "Out of stock");
                outboxService.publish(topics.getRestaurantRejected(), rejectedEvent.getOrderId().toString(), rejectedEvent);
            }
            case IN_PROGRESS -> {
                ticket.setStatus(KitchenTicketStatus.IN_PROGRESS);
                ticket.setStartedAt(Instant.now());
            }
            case READY -> {
                ticket.setStatus(KitchenTicketStatus.READY);
                ticket.setReadyAt(Instant.now());
                var readyEvent = OpsEventFactory.createOrderReady(ticket);
                outboxService.publish(topics.getOrderReady(), readyEvent.getOrderId().toString(), readyEvent);
            }
            default -> throw new IllegalArgumentException("Invalid status transition: " + request.status());
        }

        return KitchenTicketResponse.from(ticketRepository.save(ticket));
    }

    private void validateTransition(KitchenTicketStatus current, KitchenTicketStatus requested) {
        boolean valid = switch (current) {
            case PENDING -> requested == KitchenTicketStatus.ACCEPTED || requested == KitchenTicketStatus.REJECTED;
            case ACCEPTED -> requested == KitchenTicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> requested == KitchenTicketStatus.READY;
            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("Invalid status transition: " + current + " -> " + requested);
        }
    }

    public Page<KitchenTicketResponse> getTickets(KitchenTicketStatus status, Pageable pageable) {
        if (status != null) {
            return ticketRepository.findByStatus(status, pageable).map(KitchenTicketResponse::from);
        }
        return ticketRepository.findAll(pageable).map(KitchenTicketResponse::from);
    }

    public KitchenTicketResponse getTicket(UUID orderId) {
        return ticketRepository.findByOrderId(orderId)
                .map(KitchenTicketResponse::from)
                .orElseThrow(() -> new KitchenTicketNotFoundException(orderId));
    }

    public void cancelKitchenTicket(UUID orderId, String reason) {
        log.info("Cancelling kitchen ticket orderId={} reason={}", orderId, reason);

        var ticket = ticketRepository.findByOrderId(orderId).orElse(null);
        if (ticket == null) {
            log.warn("No kitchen ticket found for cancelled order orderId={}", orderId);
            return;
        }

        // Only cancel if not already completed
        if (ticket.getStatus() != KitchenTicketStatus.READY) {
            ticket.setStatus(KitchenTicketStatus.CANCELLED);
            ticketRepository.save(ticket);
            log.info("Kitchen ticket cancelled orderId={}", orderId);
        } else {
            log.info("Kitchen ticket already ready, not cancelling orderId={}", orderId);
        }
    }
}
