package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPayoutRepository extends JpaRepository<PayoutEntity, UUID> {
    Optional<PayoutEntity> findByRefundId(UUID refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payout from PayoutEntity payout where payout.payoutId = :payoutId")
    Optional<PayoutEntity> findByIdForUpdate(UUID payoutId);

    List<PayoutEntity> findByStatusInOrderByUpdatedAtAsc(Collection<PayoutStatus> statuses);
}
