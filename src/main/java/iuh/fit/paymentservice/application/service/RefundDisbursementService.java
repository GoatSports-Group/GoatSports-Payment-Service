package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.domain.entity.Refund;

import java.util.UUID;

public interface RefundDisbursementService {
    Refund claim(UUID refundId, UUID payerId);

    void processAutomaticRefund(UUID refundId);

    void processWaitingAutomaticRefunds(UUID payerId);

    void reconcileProcessingPayouts();
}
