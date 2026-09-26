package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.SettlementEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSettlementRepository extends JpaRepository<SettlementEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settlement from SettlementEntity settlement where settlement.settlementId = :settlementId")
    Optional<SettlementEntity> findByIdForUpdate(UUID settlementId);

    List<SettlementEntity> findTop50ByPayeeIdOrderByCreatedAtDesc(UUID payeeId);

    @Query("select coalesce(sum(settlement.payoutAmount), 0) from SettlementEntity settlement "
            + "where settlement.payeeId = :payeeId and settlement.status not in :excluded")
    Double sumPayoutAmount(UUID payeeId, Collection<SettlementStatus> excluded);
}
