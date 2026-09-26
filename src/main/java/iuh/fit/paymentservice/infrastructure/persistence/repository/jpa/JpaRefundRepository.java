package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaRefundRepository extends JpaRepository<RefundEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from RefundEntity refund where refund.refundId = :refundId")
    Optional<RefundEntity> findByIdForUpdate(UUID refundId);

    Optional<RefundEntity> findByIdempotencyKey(String idempotencyKey);

    List<RefundEntity> findByPaymentIdAndStatusIn(
            UUID paymentId,
            Collection<RefundStatus> statuses
    );

    @Query("select refund from RefundEntity refund join refund.payment payment "
            + "where payment.payerId = :payerId and refund.autoDisburse = true and refund.status = :status "
            + "order by refund.createdAt asc")
    List<RefundEntity> findAutomaticByPayerIdAndStatus(UUID payerId, RefundStatus status);

    @Query("select refund from RefundEntity refund join refund.payment payment "
            + "where payment.payerId = :payerId order by refund.createdAt desc")
    List<RefundEntity> findByPayerId(UUID payerId);

    List<RefundEntity> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<RefundStatus> statuses,
            LocalDateTime updatedBefore,
            Pageable pageable
    );
}
