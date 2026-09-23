package com.food.delivery.delivery.service;

import com.food.delivery.delivery.model.DeliveryEntity;
import com.food.delivery.delivery.model.DeliveryStatus;
import com.food.delivery.delivery.repository.DeliveryCancellationTombstoneRepository;
import com.food.delivery.delivery.repository.DeliveryRepository;
import com.food.delivery.delivery.repository.ReadyDeliveryOrderRepository;
import fd.delivery.DeliveryRequestedV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    DeliveryRepository deliveryRepository;

    @Mock
    DeliveryCancellationTombstoneRepository cancellationTombstoneRepository;

    @Mock
    ReadyDeliveryOrderRepository readyOrderRepository;

    @Mock
    AssignmentService assignmentService;

    @InjectMocks
    DeliveryService deliveryService;

    @Test
    void processDeliveryRequest_createsDeliveryAndOffersIt() {
        var event = deliveryRequested();
        when(deliveryRepository.findByOrderId(event.getOrderId())).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deliveryService.processDeliveryRequest(event);

        verify(assignmentService).offerToAvailableCourier(any(DeliveryEntity.class));
    }

    @Test
    void deliveryRequestAfterCancellation_isIgnored() {
        var event = deliveryRequested();
        when(cancellationTombstoneRepository.existsById(event.getOrderId())).thenReturn(true);

        deliveryService.processDeliveryRequest(event);

        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(assignmentService);
    }

    @Test
    void duplicateDeliveryRequest_isIgnored() {
        var event = deliveryRequested();
        var existing = new DeliveryEntity();
        existing.setOrderId(event.getOrderId());
        when(deliveryRepository.findByOrderId(event.getOrderId())).thenReturn(Optional.of(existing));

        deliveryService.processDeliveryRequest(event);

        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(assignmentService);
    }

    @Test
    void readyEventBeforeDeliveryRequest_isRemembered() {
        var event = deliveryRequested();
        when(deliveryRepository.findByOrderId(event.getOrderId())).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(readyOrderRepository.existsById(event.getOrderId())).thenReturn(true);

        deliveryService.processDeliveryRequest(event);

        var delivery = ArgumentCaptor.forClass(DeliveryEntity.class);
        verify(deliveryRepository, times(2)).save(delivery.capture());
        assertThat(delivery.getAllValues().get(1).isReadyForPickup()).isTrue();
        verify(assignmentService).offerToAvailableCourier(any(DeliveryEntity.class));
    }

    @Test
    void cancelDelivery_cancelsDeliveryAndCurrentAssignment() {
        var orderId = UUID.randomUUID();
        var delivery = new DeliveryEntity();
        delivery.setOrderId(orderId);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        when(deliveryRepository.findForUpdateByOrderId(orderId)).thenReturn(Optional.of(delivery));

        deliveryService.cancelDelivery(orderId, "Customer request");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        verify(assignmentService).cancelCurrentAssignment(delivery);
    }

    @Test
    void cancelDeliveredDelivery_createsTombstoneButDoesNotChangeDelivery() {
        var orderId = UUID.randomUUID();
        var delivery = new DeliveryEntity();
        delivery.setOrderId(orderId);
        delivery.setStatus(DeliveryStatus.DELIVERED);
        when(cancellationTombstoneRepository.existsById(orderId)).thenReturn(false);
        when(deliveryRepository.findForUpdateByOrderId(orderId)).thenReturn(Optional.of(delivery));

        deliveryService.cancelDelivery(orderId, "Too late");

        verify(cancellationTombstoneRepository).save(any());
        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(assignmentService);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    private DeliveryRequestedV1 deliveryRequested() {
        return new DeliveryRequestedV1(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
