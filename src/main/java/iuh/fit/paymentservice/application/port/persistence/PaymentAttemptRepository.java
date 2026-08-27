package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository {
    PaymentAttempt save(PaymentAttempt paymentAttempt);

    Optional<PaymentAttempt> findById(UUID attemptId);

    Optional<PaymentAttempt> findByIdForUpdate(UUID attemptId);

    Optional<PaymentAttempt> findLatestByPaymentIdAndStatuses(
            UUID paymentId,
            Collection<PaymentAttemptStatus> statuses
    );

    Optional<PaymentAttempt> findLatestByPaymentIdAndProviderAndMethodAndStatuses(
            UUID paymentId,
            PaymentProvider provider,
            PaymentMethod method,
            Collection<PaymentAttemptStatus> statuses
    );

    List<PaymentAttempt> findReconciliationCandidates(
            PaymentProvider provider,
            Collection<PaymentAttemptStatus> statuses,
            LocalDateTime updatedBefore,
            int limit
    );
}
