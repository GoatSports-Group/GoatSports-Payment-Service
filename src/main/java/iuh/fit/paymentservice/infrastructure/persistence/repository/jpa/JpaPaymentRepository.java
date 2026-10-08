package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentEntity payment where payment.paymentId = :paymentId")
    Optional<PaymentEntity> findByIdForUpdate(UUID paymentId);

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(payment.payeeAmount), 0) from PaymentEntity payment where payment.payeeId = :payeeId "
            + "and payment.status in :statuses and payment.paidAt <= :paidBefore")
    Double sumPayeeAmount(UUID payeeId, java.util.Collection<iuh.fit.paymentservice.domain.enums.PaymentStatus> statuses,
                          java.time.LocalDateTime paidBefore);

    List<PaymentEntity> findTop200ByPayeeIdAndStatusInOrderByPaidAtDesc(
            UUID payeeId, java.util.Collection<iuh.fit.paymentservice.domain.enums.PaymentStatus> statuses);

    Optional<PaymentEntity> findByReferenceTypeAndReferenceIdAndPurpose(
            PaymentReferenceType referenceType,
            UUID referenceId,
            PaymentPurpose purpose
    );

    List<PaymentEntity> findByReferenceTypeAndReferenceIdInOrderByCreatedAtAsc(
            PaymentReferenceType referenceType,
            Set<UUID> referenceIds
    );
}
