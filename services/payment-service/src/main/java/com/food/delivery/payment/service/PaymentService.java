package com.food.delivery.payment.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.payment.config.KafkaTopics;
import com.food.delivery.payment.model.PaymentStatus;
import com.food.delivery.payment.repository.PaymentRepository;
import com.food.delivery.payment.util.PaymentFactory;
import fd.payment.FeeRequestedV1;
import fd.payment.PaymentRequestedV1;
import fd.payment.RefundRequestedV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    @Transactional
    public void processPaymentRequest(PaymentRequestedV1 event) {
        log.info("Processing payment request orderId={} amount={}", event.getOrderId(), event.getAmount());

        var orderId = event.getOrderId();

        // Check for existing SUCCESSFUL payment
        var existingAuthorized = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED);
        if (existingAuthorized.isPresent()) {
            log.debug("Payment already authorized for orderId={}", orderId);
            return; // Skip - already successfully paid
        }
        // PaymentRequestedV1 is delivered at least once. Any existing
        // non-authorized payment is also a completed attempt and must not
        // create a second row for the unique order_id.
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            log.debug("Payment attempt already exists for orderId={}", orderId);
            return;
        }

        var payment = PaymentFactory.createPayment(event);
        payment.setStatus(PaymentStatus.PENDING);

        // Simulate payment gateway call
        if (simulatePaymentGateway(event.getAmount())) {
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setAuthorizationCode("AUTH-" + UUID.randomUUID());
            paymentRepository.save(payment);

            var authorizedEvent = PaymentFactory.createPaymentAuthorized(payment);
            outboxService.publish(topics.getPaymentAuthorized(), authorizedEvent.getOrderId().toString(), authorizedEvent);

            log.info("Payment authorized orderId={} authCode={}", orderId, payment.getAuthorizationCode());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Insufficient funds");
            paymentRepository.save(payment);

            var failedEvent = PaymentFactory.createPaymentFailed(payment);
            outboxService.publish(topics.getPaymentFailed(), failedEvent.getOrderId().toString(), failedEvent);

            log.warn("Payment failed orderId={} reason={}", orderId, payment.getFailureReason());
        }
    }

    private boolean simulatePaymentGateway(BigDecimal amount) {
        // Deterministic simulation: amounts above 100 fail
        return amount.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    @Transactional
    public void processRefundRequest(RefundRequestedV1 event) {
        log.info("Processing refund request orderId={} amount={} reason={}", event.getOrderId(), event.getAmount(), event.getReason());

        var payment = paymentRepository.findByOrderIdAndStatus(event.getOrderId(), PaymentStatus.AUTHORIZED)
                .orElse(null);
        if (payment == null) {
            if (paymentRepository.findByOrderIdAndStatus(event.getOrderId(), PaymentStatus.REFUNDED).isPresent()) {
                log.debug("Refund already processed for orderId={}", event.getOrderId());
                return;
            }
            throw new IllegalStateException("No authorized payment found for refund orderId=" + event.getOrderId());
        }

        // Simulate refund processing
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        var refundCompletedEvent = PaymentFactory.createRefundCompleted(payment, event.getReason());
        outboxService.publish(topics.getRefundCompleted(), payment.getOrderId().toString(), refundCompletedEvent);

        log.info("Refund processed successfully orderId={} amount={}", event.getOrderId(), event.getAmount());
    }

    @Transactional
    public void processFeeRequest(FeeRequestedV1 event) {
        log.info("Processing fee request orderId={} fee={} reason={}", event.getOrderId(), event.getFee(), event.getReason());

        var payment = paymentRepository.findByOrderIdAndStatus(event.getOrderId(), PaymentStatus.AUTHORIZED)
                .orElseThrow(() -> new IllegalStateException("No authorized payment found for fee charging orderId={}" + event.getOrderId()));

        // Simulate fee charging (TODO: instead of this it should be charging customer's card)
        var feeCharged = simulateFeeCharging(event.getFee());

        if (feeCharged) {
            log.info("Fee charged successfully orderId={} amount={} reason={}", event.getOrderId(), event.getFee(), event.getReason());

            var feeChargedEvent = PaymentFactory.createFeeCharged(event);
            outboxService.publish(topics.getFeeCharged(), feeChargedEvent.getOrderId().toString(), feeChargedEvent);
        } else {
            log.warn("Fee charging failed orderId={} amount={} reason={}", event.getOrderId(), event.getFee(), event.getReason());

            var feeFailedEvent = PaymentFactory.createFeeFailed(event, "Payment gateway error");
            outboxService.publish(topics.getFeeFailed(), feeFailedEvent.getOrderId().toString(), feeFailedEvent);
        }
    }

    private boolean simulateFeeCharging(BigDecimal fee) {
        // Deterministic simulation: fees always succeed
        return true;
    }
}
