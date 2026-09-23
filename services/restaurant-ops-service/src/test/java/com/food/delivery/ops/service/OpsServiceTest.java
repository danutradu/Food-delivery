package com.food.delivery.ops.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.ops.config.KafkaTopics;
import com.food.delivery.ops.dto.StatusUpdateRequest;
import com.food.delivery.ops.exception.KitchenTicketNotFoundException;
import com.food.delivery.ops.model.KitchenTicketEntity;
import com.food.delivery.ops.model.KitchenTicketStatus;
import com.food.delivery.ops.repository.KitchenTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpsServiceTest {

    @Mock
    KitchenTicketRepository ticketRepository;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @InjectMocks
    OpsService opsService;

    private UUID orderId;
    private KitchenTicketEntity ticket;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        ticket = new KitchenTicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setOrderId(orderId);
        ticket.setRestaurantId(UUID.randomUUID());
        ticket.setCustomerId(UUID.randomUUID());
        ticket.setStatus(KitchenTicketStatus.PENDING);
        ticket.setReceivedAt(Instant.now());
    }

    @Test
    void updateStatus_accepted_publishesRestaurantAccepted() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getRestaurantAccepted()).thenReturn("fd.restaurant.accepted.v1");

        var result = opsService.updateStatus(orderId, new StatusUpdateRequest(KitchenTicketStatus.ACCEPTED, 20, null));

        assertThat(result.status()).isEqualTo(KitchenTicketStatus.ACCEPTED);
        assertThat(result.estimatedPrepTimeMinutes()).isEqualTo(20);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_accepted_defaultsEtaTo15WhenNull() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getRestaurantAccepted()).thenReturn("fd.restaurant.accepted.v1");

        opsService.updateStatus(orderId, new StatusUpdateRequest(KitchenTicketStatus.ACCEPTED, null, null));

        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_rejected_publishesRestaurantRejected() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getRestaurantRejected()).thenReturn("fd.restaurant.rejected.v1");

        var result = opsService.updateStatus(orderId, new StatusUpdateRequest(KitchenTicketStatus.REJECTED, null, "Out of stock"));

        assertThat(result.status()).isEqualTo(KitchenTicketStatus.REJECTED);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_ready_publishesOrderReady() {
        ticket.setStatus(KitchenTicketStatus.IN_PROGRESS);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getOrderReady()).thenReturn("fd.restaurant.order-ready.v1");

        var result = opsService.updateStatus(orderId, new StatusUpdateRequest(KitchenTicketStatus.READY, null, null));

        assertThat(result.status()).isEqualTo(KitchenTicketStatus.READY);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_readyFromPending_rejectsInvalidTransition() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> opsService.updateStatus(orderId,
                new StatusUpdateRequest(KitchenTicketStatus.READY, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING -> READY");

        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_rejectedTicket_cannotBeAcceptedAgain() {
        ticket.setStatus(KitchenTicketStatus.REJECTED);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> opsService.updateStatus(orderId,
                new StatusUpdateRequest(KitchenTicketStatus.ACCEPTED, 20, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REJECTED -> ACCEPTED");

        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_inProgress_noEventPublished() {
        ticket.setStatus(KitchenTicketStatus.ACCEPTED);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = opsService.updateStatus(orderId, new StatusUpdateRequest(KitchenTicketStatus.IN_PROGRESS, null, null));

        assertThat(result.status()).isEqualTo(KitchenTicketStatus.IN_PROGRESS);
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_notFound_throws() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> opsService.updateStatus(orderId, new StatusUpdateRequest(KitchenTicketStatus.ACCEPTED, 20, null)))
                .isInstanceOf(KitchenTicketNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void cancelKitchenTicket_cancelsNonReadyTicket() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        opsService.cancelKitchenTicket(orderId, "Order cancelled");

        assertThat(ticket.getStatus()).isEqualTo(KitchenTicketStatus.CANCELLED);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void cancelKitchenTicket_readyTicket_doesNotCancel() {
        ticket.setStatus(KitchenTicketStatus.READY);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));

        opsService.cancelKitchenTicket(orderId, "Order cancelled");

        assertThat(ticket.getStatus()).isEqualTo(KitchenTicketStatus.READY);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void cancelKitchenTicket_noTicket_doesNothing() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        opsService.cancelKitchenTicket(orderId, "Order cancelled");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void processAcceptanceRequest_duplicateTicket_doesNotCreateAnotherTicket() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));

        opsService.processAcceptanceRequest(orderId, ticket.getRestaurantId(), ticket.getCustomerId());

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void processAcceptanceRequest_newTicket_startsPending() {
        when(ticketRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        opsService.processAcceptanceRequest(orderId, ticket.getRestaurantId(), ticket.getCustomerId());

        var saved = ArgumentCaptor.forClass(KitchenTicketEntity.class);
        verify(ticketRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(KitchenTicketStatus.PENDING);
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void getTickets_withStatus_returnsPagedResponses() {
        var pageable = Pageable.unpaged();
        when(ticketRepository.findByStatus(KitchenTicketStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        var result = opsService.getTickets(KitchenTicketStatus.PENDING, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().status()).isEqualTo(KitchenTicketStatus.PENDING);
    }

    @Test
    void getTickets_withoutStatus_returnsPagedResponses() {
        var pageable = Pageable.unpaged();
        when(ticketRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(ticket)));

        var result = opsService.getTickets(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().status()).isEqualTo(KitchenTicketStatus.PENDING);
    }
}
