package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {
    Refund save(Refund refund);

    Optional<Refund> findById(UUID refundId);

    Optional<Refund> findByIdForUpdate(UUID refundId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    List<Refund> findByPaymentIdAndStatuses(UUID paymentId, Collection<RefundStatus> statuses);

    List<Refund> findAutomaticByPayerIdAndStatus(UUID payerId, RefundStatus status);

    List<Refund> findReconciliationCandidates(
            Collection<RefundStatus> statuses,
            LocalDateTime updatedBefore,
            int limit
    );
}
