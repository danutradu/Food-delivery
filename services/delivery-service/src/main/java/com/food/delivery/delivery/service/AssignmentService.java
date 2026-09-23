package com.food.delivery.delivery.service;

import com.food.delivery.delivery.exception.AssignmentNotFoundException;
import com.food.delivery.delivery.exception.AssignmentStateConflictException;
import com.food.delivery.delivery.exception.DeliveryNotFoundException;
import com.food.delivery.delivery.model.*;
import com.food.delivery.delivery.repository.CourierAssignmentRepository;
import com.food.delivery.delivery.repository.CourierRepository;
import com.food.delivery.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final DeliveryRepository deliveryRepository;
    private final CourierAssignmentRepository assignmentRepository;
    private final CourierRepository courierRepository;

    @Value("${delivery.assignment.offer-timeout-seconds:30}")
    private long offerTimeoutSeconds;

    @Transactional
    public void offerToAvailableCourier(DeliveryEntity delivery) {
        var lockedDelivery = deliveryRepository.findForUpdateById(delivery.getId())
                .orElseThrow(() -> new DeliveryNotFoundException(delivery.getId()));
        if (lockedDelivery.getCurrentAssignmentId() != null) {
            return;
        }
        courierRepository.findAvailableForUpdate(lockedDelivery.getId()).ifPresent(courier -> {
            var now = Instant.now();
            var assignment = new CourierAssignmentEntity();
            assignment.setDeliveryId(lockedDelivery.getId());
            assignment.setCourierId(courier.getId());
            assignment.setStatus(AssignmentStatus.OFFERED);
            assignment.setOfferedAt(now);
            assignment.setExpiresAt(now.plusSeconds(offerTimeoutSeconds));
            assignmentRepository.save(assignment);
            lockedDelivery.setCurrentAssignmentId(assignment.getId());
            lockedDelivery.setStatus(DeliveryStatus.OFFERED);
            deliveryRepository.save(lockedDelivery);
            courier.setStatus(CourierStatus.OFFERED);
            courierRepository.save(courier);
        });
    }

    @Transactional
    public void cancelCurrentAssignment(DeliveryEntity delivery) {
        if (delivery.getCurrentAssignmentId() == null) {
            return;
        }
        assignmentRepository.findForUpdateById(delivery.getCurrentAssignmentId()).ifPresent(assignment -> {
            if (assignment.getStatus() == AssignmentStatus.OFFERED || assignment.getStatus() == AssignmentStatus.ACCEPTED) {
                assignment.setStatus(AssignmentStatus.CANCELLED);
                assignment.setRespondedAt(Instant.now());
                assignmentRepository.save(assignment);
                releaseCourier(assignment.getCourierId());
            }
        });
    }

    @Scheduled(fixedDelayString = "${delivery.assignment.expiry-check-ms:5000}")
    @Transactional
    public void expireOffers() {
        assignmentRepository.findByStatusAndExpiresAtBefore(AssignmentStatus.OFFERED, Instant.now()).stream()
                .map(CourierAssignmentEntity::getId)
                .forEach(this::expireAssignment);
    }

    private void expireAssignment(UUID assignmentId) {
        var context = lockAssignment(assignmentId);
        var assignment = context.assignment();
        if (assignment.getStatus() != AssignmentStatus.OFFERED || !assignment.getExpiresAt().isBefore(Instant.now())) {
            return;
        }
        assignment.setStatus(AssignmentStatus.EXPIRED);
        assignment.setRespondedAt(Instant.now());
        assignmentRepository.save(assignment);
        releaseCourierAndReassign(assignment, context.delivery());
    }

    private void releaseCourierAndReassign(CourierAssignmentEntity assignment, DeliveryEntity delivery) {
        releaseCourierState(assignment.getCourierId());
        delivery.setCurrentAssignmentId(null);
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        deliveryRepository.save(delivery);
        offerToAvailableCourier(delivery);
        dispatchOldestPendingDelivery();
    }

    private void releaseCourier(UUID courierId) {
        releaseCourierState(courierId);
        dispatchOldestPendingDelivery();
    }

    private void releaseCourierState(UUID courierId) {
        courierRepository.findById(courierId).ifPresent(courier -> {
            courier.setStatus(CourierStatus.AVAILABLE);
            courierRepository.save(courier);
        });
    }

    private void dispatchOldestPendingDelivery() {
        deliveryRepository.findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING_ASSIGNMENT)
                .ifPresent(this::offerToAvailableCourier);
    }

    private LockedAssignment lockAssignment(UUID assignmentId) {
        var preview = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        var delivery = deliveryRepository.findForUpdateById(preview.getDeliveryId())
                .orElseThrow(() -> new DeliveryNotFoundException(preview.getDeliveryId()));
        var assignment = assignmentRepository.findForUpdateById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        if (!assignment.getDeliveryId().equals(delivery.getId())
                || !assignment.getId().equals(delivery.getCurrentAssignmentId())) {
            throw new AssignmentStateConflictException("Assignment is no longer current for this delivery");
        }
        return new LockedAssignment(assignment, delivery);
    }

    private record LockedAssignment(CourierAssignmentEntity assignment, DeliveryEntity delivery) {}
}
