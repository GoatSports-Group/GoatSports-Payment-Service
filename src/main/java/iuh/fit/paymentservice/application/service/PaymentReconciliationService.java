package iuh.fit.paymentservice.application.service;

import java.util.UUID;

public interface PaymentReconciliationService {
    void reconcile(UUID attemptId);

    void cancel(UUID paymentId, UUID payerUserId, String reason);
}
