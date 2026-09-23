package com.food.delivery.payment.util;

import com.food.delivery.payment.model.PaymentEntity;
import fd.payment.*;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class PaymentFactory {

    public PaymentEntity createPayment(PaymentRequestedV1 req) {
        var payment = new PaymentEntity();
        payment.setOrderId(req.getOrderId());
        payment.setAmount(req.getAmount());
        payment.setCreatedAt(Instant.now());
        return payment;
    }

    public PaymentAuthorizedV1 createPaymentAuthorized(PaymentEntity payment) {
        return new PaymentAuthorizedV1(
                UUID.randomUUID(),
                Instant.now(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getAuthorizationCode()
        );
    }

    public PaymentFailedV1 createPaymentFailed(PaymentEntity payment) {
        return new PaymentFailedV1(
                UUID.randomUUID(),
                Instant.now(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getFailureReason()
        );
    }

    public RefundCompletedV1 createRefundCompleted(PaymentEntity payment, String reason) {
        return new RefundCompletedV1(
                UUID.randomUUID(),
                Instant.now(),
                payment.getOrderId(),
                payment.getAmount(),
                reason
        );
    }

    public FeeChargedV1 createFeeCharged(FeeRequestedV1 feeRequested) {
        return new FeeChargedV1(
                UUID.randomUUID(),
                Instant.now(),
                feeRequested.getOrderId(),
                feeRequested.getFee(),
                feeRequested.getReason()
        );
    }

    public FeeFailedV1 createFeeFailed(FeeRequestedV1 event, String failureReason) {
        return new FeeFailedV1(
                UUID.randomUUID(),
                Instant.now(),
                event.getOrderId(),
                event.getFee(),
                event.getReason(),
                failureReason
        );
    }
}
