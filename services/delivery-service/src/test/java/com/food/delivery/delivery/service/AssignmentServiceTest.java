package com.food.delivery.delivery.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.delivery.config.KafkaTopics;
import com.food.delivery.delivery.model.*;
import com.food.delivery.delivery.repository.CourierAssignmentRepository;
import com.food.delivery.delivery.repository.CourierRepository;
import com.food.delivery.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
