package com.food.delivery.delivery.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.delivery.config.KafkaTopics;
import com.food.delivery.delivery.exception.AssignmentNotFoundException;
import com.food.delivery.delivery.exception.AssignmentStateConflictException;
import com.food.delivery.delivery.model.*;
import com.food.delivery.delivery.repository.CourierAssignmentRepository;
import com.food.delivery.delivery.repository.CourierRepository;
import com.food.delivery.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    DeliveryRepository deliveryRepository;

    @Mock
    CourierAssignmentRepository assignmentRepository;

    @Mock
    CourierRepository courierRepository;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @InjectMocks
    AssignmentService assignmentService;

    private DeliveryEntity delivery;

    private CourierEntity courier;

    private CourierAssignmentEntity assignment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(assignmentService, "offerTimeoutSeconds", 30L);
        delivery = new DeliveryEntity();
        delivery.setId(UUID.randomUUID());
        delivery.setOrderId(UUID.randomUUID());
        delivery.setRestaurantId(UUID.randomUUID());
        delivery.setCustomerId(UUID.randomUUID());
        delivery.setStatus(DeliveryStatus.OFFERED);
        courier = new CourierEntity();
        courier.setId(UUID.randomUUID());
        courier.setUserId(UUID.randomUUID());
        courier.setStatus(CourierStatus.OFFERED);
        assignment = new CourierAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setDeliveryId(delivery.getId());
        assignment.setCourierId(courier.getId());
        assignment.setStatus(AssignmentStatus.OFFERED);
        assignment.setOfferedAt(Instant.now());
        assignment.setExpiresAt(Instant.now().plusSeconds(30));
        delivery.setCurrentAssignmentId(assignment.getId());
        lenient().when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        lenient().when(assignmentRepository.findForUpdateById(assignment.getId())).thenReturn(Optional.of(assignment));
        lenient().when(deliveryRepository.findForUpdateById(delivery.getId())).thenReturn(Optional.of(delivery));
    }

    @Test
    void acceptAssignment_recordsAcceptedAttemptAndPublishesEvent() {
        when(assignmentRepository.findForUpdateById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(topics.getCourierAssigned()).thenReturn("assigned");

        assignmentService.acceptAssignment(assignment.getId(), courier.getUserId());

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.ON_DELIVERY);
        verify(outboxService).publish(eq("assigned"), eq(delivery.getOrderId().toString()), any());
    }

    @Test
    void rejectAssignment_preservesHistoryAndOffersAnotherCourier() {
        var nextCourier = new CourierEntity();
        nextCourier.setId(UUID.randomUUID());
        nextCourier.setStatus(CourierStatus.AVAILABLE);
        when(assignmentRepository.findForUpdateById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(courierRepository.findAvailableForUpdate(delivery.getId())).thenReturn(Optional.of(nextCourier));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            var value = invocation.<CourierAssignmentEntity>getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });

        assignmentService.rejectAssignment(assignment.getId(), courier.getUserId());

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.OFFERED);
        assertThat(nextCourier.getStatus()).isEqualTo(CourierStatus.OFFERED);
    }

    @Test
    void pickupAndDelivery_completeAcceptedAssignment() {
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        delivery.setReadyForPickup(true);
        delivery.setStatus(DeliveryStatus.READY_FOR_PICKUP);
        courier.setStatus(CourierStatus.ON_DELIVERY);
        when(assignmentRepository.findForUpdateById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(deliveryRepository.findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING_ASSIGNMENT)).thenReturn(Optional.empty());
        when(topics.getOrderPickedUp()).thenReturn("picked-up");
        when(topics.getOrderDelivered()).thenReturn("delivered");

        assignmentService.markAsPickedUp(assignment.getId(), courier.getUserId());
        assignmentService.markAsDelivered(assignment.getId(), courier.getUserId());

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        verify(outboxService, times(2)).publish(any(), any(), any());
    }

    @Test
    void expireOffers_expiresAttemptAndOffersAnotherCourier() {
        assignment.setExpiresAt(Instant.now().minusSeconds(1));
        var nextCourier = new CourierEntity();
        nextCourier.setId(UUID.randomUUID());
        nextCourier.setStatus(CourierStatus.AVAILABLE);
        when(assignmentRepository.findByStatusAndExpiresAtBefore(eq(AssignmentStatus.OFFERED), any())).thenReturn(List.of(assignment));
        when(courierRepository.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(courierRepository.findAvailableForUpdate(delivery.getId())).thenReturn(Optional.of(nextCourier));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            var value = invocation.<CourierAssignmentEntity>getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });

        assignmentService.expireOffers();

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.EXPIRED);
        assertThat(nextCourier.getStatus()).isEqualTo(CourierStatus.OFFERED);
    }

    @Test
    void assignmentListBulkLoadsDeliveries() {
        when(assignmentRepository.findAll(ArgumentMatchers.<Specification<CourierAssignmentEntity>>any(), any(Sort.class)))
                .thenReturn(List.of(assignment));
        when(deliveryRepository.findAllById(any())).thenReturn(List.of(delivery));

        var responses = assignmentService.getAssignments(null, null, null, null, UUID.randomUUID(), true);

        assertThat(responses).hasSize(1);
        verify(deliveryRepository).findAllById(any());
        verify(deliveryRepository, never()).findById(any());
    }

    @Test
    void adminCanReadAssignment() {
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(deliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));

        var response = assignmentService.getAssignment(assignment.getId(), UUID.randomUUID(), true);

        assertThat(response.assignmentId()).isEqualTo(assignment.getId());
        assertThat(response.orderId()).isEqualTo(delivery.getOrderId());
    }

    @Test
    void invalidAssignmentTransition_isAConflict() {
        assignment.setStatus(AssignmentStatus.REJECTED);
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> assignmentService.acceptAssignment(assignment.getId(), courier.getUserId()))
                .isInstanceOf(AssignmentStateConflictException.class);
    }

    @Test
    void expiredOffer_cannotBeAccepted() {
        assignment.setExpiresAt(Instant.now().minusSeconds(1));
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> assignmentService.acceptAssignment(assignment.getId(), courier.getUserId()))
                .isInstanceOf(AssignmentStateConflictException.class)
                .hasMessageContaining("expired");

        verify(assignmentRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void pickupBeforeDeliveryReady_isAConflict() {
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> assignmentService.markAsPickedUp(assignment.getId(), courier.getUserId()))
                .isInstanceOf(AssignmentStateConflictException.class)
                .hasMessageContaining("READY_FOR_PICKUP");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void deliveryBeforePickup_isAConflict() {
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        delivery.setStatus(DeliveryStatus.READY_FOR_PICKUP);
        when(courierRepository.findByUserId(courier.getUserId())).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> assignmentService.markAsDelivered(assignment.getId(), courier.getUserId()))
                .isInstanceOf(AssignmentStateConflictException.class)
                .hasMessageContaining("PICKED_UP");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void offerToAvailableCourier_withoutCourier_leavesDeliveryUnassigned() {
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        delivery.setCurrentAssignmentId(null);
        when(deliveryRepository.findForUpdateById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(courierRepository.findAvailableForUpdate(delivery.getId())).thenReturn(Optional.empty());

        assignmentService.offerToAvailableCourier(delivery);

        assertThat(delivery.getCurrentAssignmentId()).isNull();
        verify(assignmentRepository, never()).save(any());
        verify(courierRepository, never()).save(any());
    }

    @Test
    void assignmentOwnedByAnotherCourier_isHidden() {
        when(assignmentRepository.findForUpdateById(assignment.getId())).thenReturn(Optional.of(assignment));
        var otherCourier = new CourierEntity();
        otherCourier.setId(UUID.randomUUID());
        when(courierRepository.findByUserId(any())).thenReturn(Optional.of(otherCourier));

        assertThatThrownBy(() -> assignmentService.acceptAssignment(assignment.getId(), UUID.randomUUID()))
                .isInstanceOf(AssignmentNotFoundException.class);
    }
}
