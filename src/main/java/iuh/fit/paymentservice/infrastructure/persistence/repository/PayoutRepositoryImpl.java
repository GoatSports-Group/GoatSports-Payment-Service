package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.PayoutRepository;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.PayoutEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PayoutRepositoryImpl implements PayoutRepository {
    private final JpaPayoutRepository jpaRepository;
    private final PayoutEntityMapper mapper;

    @Override
    public Payout save(Payout payout) {
        PayoutEntity payoutEntity = payout.getPayoutId() == null
                ? mapper.toEntity(payout)
                : jpaRepository.findById(payout.getPayoutId())
                        .map(existing -> {
                            mapper.updateEntity(payout, existing);
                            return existing;
                        })
                        .orElseGet(() -> mapper.toEntity(payout));
        return mapper.toDomain(jpaRepository.saveAndFlush(payoutEntity));
    }

    @Override
    public Optional<Payout> findByRefundId(UUID refundId) {
        return jpaRepository.findByRefundId(refundId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payout> findBySettlementId(UUID settlementId) {
        return jpaRepository.findBySettlementId(settlementId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payout> findByIdForUpdate(UUID payoutId) {
        return jpaRepository.findByIdForUpdate(payoutId).map(mapper::toDomain);
    }

    @Override
    public List<Payout> findByStatuses(Collection<PayoutStatus> statuses) {
        return jpaRepository.findByStatusInOrderByUpdatedAtAsc(statuses).stream().map(mapper::toDomain).toList();
    }
}
