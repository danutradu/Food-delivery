package com.food.delivery.payment.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.payment.config.KafkaTopics;
import com.food.delivery.payment.model.PaymentEntity;
import com.food.delivery.payment.model.PaymentStatus;
import com.food.delivery.payment.repository.PaymentRepository;
import fd.payment.FeeRequestedV1;
import fd.payment.PaymentRequestedV1;
import fd.payment.RefundRequestedV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @InjectMocks
    PaymentService paymentService;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
    }

    @Test
    void processPaymentRequest_alreadyAuthorized_skips() {
        var existing = new PaymentEntity();
        existing.setId(UUID.randomUUID());
        existing.setOrderId(orderId);
        existing.setStatus(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.of(existing));

        var event = new PaymentRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"));
        paymentService.processPaymentRequest(event);

        verify(paymentRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void processPaymentRequest_existingFailedAttempt_skipsDuplicate() {
        var existing = new PaymentEntity();
        existing.setId(UUID.randomUUID());
        existing.setOrderId(orderId);
        existing.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        var event = new PaymentRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"));
        paymentService.processPaymentRequest(event);

        verify(paymentRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void processPaymentRequest_largeAmount_fails() {
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getPaymentFailed()).thenReturn("fd.payment.failed.v1");

        // amount > 100 always fails in simulation
        var event = new PaymentRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("100.01"));
        paymentService.processPaymentRequest(event);

        verify(outboxService).publish(eq("fd.payment.failed.v1"), anyString(), any());
    }

    @Test
    void processPaymentRequest_normalAmount_authorizes() {
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getPaymentAuthorized()).thenReturn("fd.payment.authorized.v1");

        var event = new PaymentRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("5.00"));
        paymentService.processPaymentRequest(event);

        verify(outboxService).publish(eq("fd.payment.authorized.v1"), eq(orderId.toString()), any());
    }

    @Test
    void processRefundRequest_noAuthorizedPayment_throws() {
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.empty());

        var event = new RefundRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "Cancelled");
        assertThatThrownBy(() -> paymentService.processRefundRequest(event))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processRefundRequest_authorizedPayment_refundsAndPublishes() {
        var payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal("12.00"));
        payment.setStatus(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getRefundCompleted()).thenReturn("fd.payment.refund-completed.v1");

        var event = new RefundRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "Cancelled");
        paymentService.processRefundRequest(event);

        verify(outboxService).publish(eq("fd.payment.refund-completed.v1"), anyString(), any());
    }

    @Test
    void processRefundRequest_alreadyRefundedPayment_isIdempotent() {
        var payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(orderId);
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED))
                .thenReturn(Optional.of(payment));

        var event = new RefundRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "Cancelled");
        paymentService.processRefundRequest(event);

        verify(paymentRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void processFeeRequest_noAuthorizedPayment_throws() {
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.empty());

        var event = new FeeRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("5.00"), "Cancellation fee");
        assertThatThrownBy(() -> paymentService.processFeeRequest(event))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processFeeRequest_authorizedPayment_publishesFeeCharged() {
        var payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(orderId);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED))
                .thenReturn(Optional.of(payment));
        when(topics.getFeeCharged()).thenReturn("fd.payment.fee-charged.v1");

        var event = new FeeRequestedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("5.00"), "Cancellation fee");
        paymentService.processFeeRequest(event);

        verify(outboxService).publish(eq("fd.payment.fee-charged.v1"), eq(orderId.toString()), any());
    }
}
