package com.food.delivery.delivery.service;

import com.food.delivery.delivery.model.DeliveryCancellationTombstoneEntity;
import com.food.delivery.delivery.model.DeliveryStatus;
import com.food.delivery.delivery.model.ReadyDeliveryOrderEntity;
import com.food.delivery.delivery.repository.DeliveryCancellationTombstoneRepository;
import com.food.delivery.delivery.repository.DeliveryRepository;
import com.food.delivery.delivery.repository.ReadyDeliveryOrderRepository;
import com.food.delivery.delivery.util.DeliveryFactory;
import fd.delivery.DeliveryRequestedV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryCancellationTombstoneRepository cancellationTombstoneRepository;
    private final ReadyDeliveryOrderRepository readyOrderRepository;
    private final AssignmentService assignmentService;

    @Transactional
    public void processDeliveryRequest(DeliveryRequestedV1 event) {
        if (cancellationTombstoneRepository.existsById(event.getOrderId())) {
            log.info("Ignoring delivery request for cancelled order orderId={}", event.getOrderId());
            return;
        }
        if (deliveryRepository.findByOrderId(event.getOrderId()).isPresent()) {
            return;
        }
        var delivery = deliveryRepository.save(DeliveryFactory.createDelivery(event));
        // The ready event normally follows restaurant acceptance, but the two
        // Kafka topics are independently ordered. Preserve readiness if it won
        // the race with this delivery request.
        if (readyOrderRepository.existsById(event.getOrderId())) {
            delivery.setReadyForPickup(true);
            deliveryRepository.save(delivery);
        }
        assignmentService.offerToAvailableCourier(delivery);
    }

    @Transactional
    public void markReadyForPickup(UUID orderId) {
        if (!readyOrderRepository.existsById(orderId)) {
            var ready = new ReadyDeliveryOrderEntity();
            ready.setOrderId(orderId);
            ready.setReadyAt(Instant.now());
            readyOrderRepository.save(ready);
        }
        deliveryRepository.findForUpdateByOrderId(orderId).ifPresent(delivery -> {
            delivery.setReadyForPickup(true);
            if (delivery.getStatus() == DeliveryStatus.ASSIGNED) {
                delivery.setStatus(DeliveryStatus.READY_FOR_PICKUP);
            }
            deliveryRepository.save(delivery);
        });
    }

    @Transactional
    public void cancelDelivery(UUID orderId, String reason) {
        if (!cancellationTombstoneRepository.existsById(orderId)) {
            var cancellation = new DeliveryCancellationTombstoneEntity();
            cancellation.setOrderId(orderId);
            cancellation.setCancelledAt(Instant.now());
            cancellation.setReason(reason);
            cancellationTombstoneRepository.save(cancellation);
        }
        var delivery = deliveryRepository.findForUpdateByOrderId(orderId).orElse(null);
        if (delivery == null || delivery.getStatus() == DeliveryStatus.DELIVERED) {
            return;
        }
        delivery.setStatus(DeliveryStatus.CANCELLED);
        deliveryRepository.save(delivery);
        assignmentService.cancelCurrentAssignment(delivery);
        log.info("Delivery cancelled orderId={} reason={}", orderId, reason);
    }
}
