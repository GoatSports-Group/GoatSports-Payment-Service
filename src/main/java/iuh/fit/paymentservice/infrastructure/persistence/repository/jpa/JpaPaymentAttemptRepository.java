package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaPaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PaymentAttemptEntity attempt where attempt.attemptId = :attemptId")
    Optional<PaymentAttemptEntity> findByIdForUpdate(UUID attemptId);

    Optional<PaymentAttemptEntity> findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
            UUID paymentId,
            Collection<PaymentAttemptStatus> statuses
    );

    List<PaymentAttemptEntity> findByProviderAndStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            PaymentProvider provider,
            Collection<PaymentAttemptStatus> statuses,
            LocalDateTime updatedBefore,
            Pageable pageable
    );
}
