package iuh.fit.paymentservice.application.service;

import java.util.UUID;

public interface RefundReconciliationService {
    void reconcile(UUID refundId);
}
