package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.PayoutRepository;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
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
        return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toEntity(payout)));
    }

    @Override
    public Optional<Payout> findByRefundId(UUID refundId) {
        return jpaRepository.findByRefundId(refundId).map(mapper::toDomain);
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
