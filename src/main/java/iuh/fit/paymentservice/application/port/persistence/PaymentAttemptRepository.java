package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository {
    PaymentAttempt save(PaymentAttempt paymentAttempt);

    Optional<PaymentAttempt> findLatestByPaymentIdAndStatuses(
            UUID paymentId,
            Collection<PaymentAttemptStatus> statuses
    );
}
