package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;

import java.util.UUID;

public interface PaymentDomainEventService {
    void recordPaymentResult(Payment payment, PaymentAttempt attempt, UUID actorId);

    void recordRefundResult(Refund refund, PaymentStatus paymentStatus);
}
