package com.food.delivery.delivery.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.delivery.config.KafkaTopics;
import com.food.delivery.delivery.dto.AssignmentResponse;
import com.food.delivery.delivery.exception.AssignmentNotFoundException;
import com.food.delivery.delivery.exception.AssignmentStateConflictException;
import com.food.delivery.delivery.exception.CourierNotFoundException;
import com.food.delivery.delivery.exception.DeliveryNotFoundException;
import com.food.delivery.delivery.model.*;
import com.food.delivery.delivery.repository.CourierAssignmentRepository;
import com.food.delivery.delivery.repository.CourierRepository;
import com.food.delivery.delivery.repository.DeliveryRepository;
import com.food.delivery.delivery.util.DeliveryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final DeliveryRepository deliveryRepository;
    private final CourierAssignmentRepository assignmentRepository;
    private final CourierRepository courierRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    @Value("${delivery.assignment.offer-timeout-seconds:30}")
    private long offerTimeoutSeconds;

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignments(Boolean active, AssignmentStatus status, UUID orderId,
                                                   UUID courierId, UUID userId, boolean admin) {
        Specification<CourierAssignmentEntity> specification = (root, query, builder) -> builder.conjunction();
        var effectiveCourierId = admin ? courierId : courierRepository.findByUserId(userId)
                .orElseThrow(() -> new CourierNotFoundException(userId))
                .getId();
        if (effectiveCourierId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("courierId"), effectiveCourierId));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (active != null) {
            specification = specification.and((root, query, builder) -> active
                    ? root.get("status").in(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED)
                    : builder.not(root.get("status").in(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED)));
        }
        if (orderId != null) {
            var delivery = deliveryRepository.findByOrderId(orderId);
            if (delivery.isEmpty()) {
                return List.of();
            }
            var deliveryId = delivery.get().getId();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("deliveryId"), deliveryId));
        }
        var assignments = assignmentRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "offeredAt"));
        var deliveryIds = assignments.stream().map(CourierAssignmentEntity::getDeliveryId).collect(Collectors.toSet());
        var deliveries = deliveryRepository.findAllById(deliveryIds).stream()
                .collect(Collectors.toMap(DeliveryEntity::getId, Function.identity()));
        return assignments.stream()
                .map(assignment -> AssignmentResponse.from(assignment,
                        Optional.ofNullable(deliveries.get(assignment.getDeliveryId()))
                                .orElseThrow(() -> new DeliveryNotFoundException(assignment.getDeliveryId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(UUID assignmentId, UUID userId, boolean admin) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        authorizeCourier(assignment, userId, admin);
        return AssignmentResponse.from(assignment, findDelivery(assignment.getDeliveryId()));
    }

    @Transactional
    public void acceptAssignment(UUID assignmentId, UUID userId) {
        var context = lockedAssignment(assignmentId, userId);
        var assignment = context.assignment();
        var delivery = context.delivery();
        requireStatus(assignment, AssignmentStatus.OFFERED);
        if (assignment.getExpiresAt().isBefore(Instant.now())) {
            throw new AssignmentStateConflictException("Assignment offer has expired");
        }
        requireDeliveryStatus(delivery, DeliveryStatus.OFFERED);
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        assignment.setRespondedAt(Instant.now());
        assignmentRepository.save(assignment);
        delivery.setStatus(delivery.isReadyForPickup()
                ? DeliveryStatus.READY_FOR_PICKUP : DeliveryStatus.ASSIGNED);
        deliveryRepository.save(delivery);
        var courier = courierRepository.findById(assignment.getCourierId())
                .orElseThrow(() -> new CourierNotFoundException(assignment.getCourierId()));
        courier.setStatus(CourierStatus.ON_DELIVERY);
        courierRepository.save(courier);
        outboxService.publish(topics.getCourierAssigned(), delivery.getOrderId().toString(),
                DeliveryFactory.createCourierAssigned(delivery, assignment));
    }

    @Transactional
    public void rejectAssignment(UUID assignmentId, UUID userId) {
        var context = lockedAssignment(assignmentId, userId);
        var assignment = context.assignment();
        requireStatus(assignment, AssignmentStatus.OFFERED);
        requireDeliveryStatus(context.delivery(), DeliveryStatus.OFFERED);
        assignment.setStatus(AssignmentStatus.REJECTED);
        assignment.setRespondedAt(Instant.now());
        assignmentRepository.save(assignment);
        releaseCourierAndReassign(assignment, context.delivery());
    }

    @Transactional
    public void markAsPickedUp(UUID assignmentId, UUID userId) {
        var context = lockedAssignment(assignmentId, userId);
        var assignment = context.assignment();
        var delivery = context.delivery();
        requireStatus(assignment, AssignmentStatus.ACCEPTED);
        requireDeliveryStatus(delivery, DeliveryStatus.READY_FOR_PICKUP);
        delivery.setStatus(DeliveryStatus.PICKED_UP);
        deliveryRepository.save(delivery);
        outboxService.publish(topics.getOrderPickedUp(), delivery.getOrderId().toString(),
                DeliveryFactory.createOrderPickedUp(delivery, assignment));
    }

    @Transactional
    public void markAsDelivered(UUID assignmentId, UUID userId) {
        var context = lockedAssignment(assignmentId, userId);
        var assignment = context.assignment();
        var delivery = context.delivery();
        requireStatus(assignment, AssignmentStatus.ACCEPTED);
        requireDeliveryStatus(delivery, DeliveryStatus.PICKED_UP);
        delivery.setStatus(DeliveryStatus.DELIVERED);
        deliveryRepository.save(delivery);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setCompletedAt(Instant.now());
        assignmentRepository.save(assignment);
        outboxService.publish(topics.getOrderDelivered(), delivery.getOrderId().toString(),
                DeliveryFactory.createOrderDelivered(delivery, assignment));
        releaseCourier(assignment.getCourierId());
    }

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
            if (courier.getStatus() != CourierStatus.SUSPENDED) {
                courier.setStatus(CourierStatus.AVAILABLE);
                courierRepository.save(courier);
            }
        });
    }

    private void dispatchOldestPendingDelivery() {
        deliveryRepository.findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING_ASSIGNMENT)
                .ifPresent(this::offerToAvailableCourier);
    }

    private LockedAssignment lockedAssignment(UUID assignmentId, UUID userId) {
        var context = lockAssignment(assignmentId);
        authorizeCourier(context.assignment(), userId, false);
        return context;
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

    private void authorizeCourier(CourierAssignmentEntity assignment, UUID userId, boolean admin) {
        if (admin) {
            return;
        }
        var courier = courierRepository.findByUserId(userId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignment.getId()));
        if (!courier.getId().equals(assignment.getCourierId())) {
            throw new AssignmentNotFoundException(assignment.getId());
        }
        if (courier.getStatus() == CourierStatus.SUSPENDED && assignment.getStatus() == AssignmentStatus.OFFERED) {
            throw new AssignmentStateConflictException("Courier is suspended and cannot accept this offer");
        }
    }

    private DeliveryEntity findDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    private void requireStatus(CourierAssignmentEntity assignment, AssignmentStatus expected) {
        if (assignment.getStatus() != expected) {
            throw new AssignmentStateConflictException("Assignment must be " + expected + ", current status: " + assignment.getStatus());
        }
    }

    private void requireDeliveryStatus(DeliveryEntity delivery, DeliveryStatus expected) {
        if (delivery.getStatus() != expected) {
            throw new AssignmentStateConflictException("Delivery must be " + expected + ", current status: " + delivery.getStatus());
        }
    }

    private record LockedAssignment(CourierAssignmentEntity assignment, DeliveryEntity delivery) {}
}
