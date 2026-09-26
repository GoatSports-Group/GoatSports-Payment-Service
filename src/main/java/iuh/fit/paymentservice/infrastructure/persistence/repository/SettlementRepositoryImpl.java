package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.SettlementRepository;
import iuh.fit.paymentservice.domain.entity.Settlement;
import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.SettlementEntity;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.SettlementEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepository {
    private final JpaSettlementRepository jpaRepository;
    private final SettlementEntityMapper mapper;

    @Override
    public Settlement save(Settlement settlement) {
        SettlementEntity entity = settlement.getSettlementId() == null
                ? mapper.toEntity(settlement)
                : jpaRepository.findById(settlement.getSettlementId())
                        .map(existing -> {
                            mapper.updateEntity(settlement, existing);
                            return existing;
                        })
                        .orElseGet(() -> mapper.toEntity(settlement));
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Settlement> findByIdForUpdate(UUID settlementId) {
        return jpaRepository.findByIdForUpdate(settlementId).map(mapper::toDomain);
    }

    @Override
    public List<Settlement> findRecentByPayeeId(UUID payeeId) {
        return jpaRepository.findTop50ByPayeeIdOrderByCreatedAtDesc(payeeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public double sumPayoutAmount(UUID payeeId, Collection<SettlementStatus> excluded) {
        return jpaRepository.sumPayoutAmount(payeeId, excluded);
    }
}
