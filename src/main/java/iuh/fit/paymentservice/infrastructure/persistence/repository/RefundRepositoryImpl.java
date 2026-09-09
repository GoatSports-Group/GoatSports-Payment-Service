package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.RefundEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaRefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryImpl implements RefundRepository {
    private final JpaRefundRepository jpaRefundRepository;
    private final RefundEntityMapper refundEntityMapper;

    @Override
    public Refund save(Refund refund) {
        return refundEntityMapper.toDomain(
                jpaRefundRepository.saveAndFlush(refundEntityMapper.toEntity(refund))
        );
    }

    @Override
    public Optional<Refund> findById(UUID refundId) {
        return jpaRefundRepository.findById(refundId).map(refundEntityMapper::toDomain);
    }

    @Override
    public Optional<Refund> findByIdForUpdate(UUID refundId) {
        return jpaRefundRepository.findByIdForUpdate(refundId).map(refundEntityMapper::toDomain);
    }

    @Override
    public Optional<Refund> findByIdempotencyKey(String idempotencyKey) {
        return jpaRefundRepository.findByIdempotencyKey(idempotencyKey)
                .map(refundEntityMapper::toDomain);
    }

    @Override
    public List<Refund> findByPaymentIdAndStatuses(
            UUID paymentId,
            Collection<RefundStatus> statuses
    ) {
        return jpaRefundRepository.findByPaymentIdAndStatusIn(paymentId, statuses)
                .stream()
                .map(refundEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Refund> findAutomaticByPayerIdAndStatus(UUID payerId, RefundStatus status) {
        return jpaRefundRepository.findAutomaticByPayerIdAndStatus(payerId, status)
                .stream().map(refundEntityMapper::toDomain).toList();
    }

    @Override
    public List<Refund> findReconciliationCandidates(
            Collection<RefundStatus> statuses,
            LocalDateTime updatedBefore,
            int limit
    ) {
        return jpaRefundRepository.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        statuses,
                        updatedBefore,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(refundEntityMapper::toDomain)
                .toList();
    }
}
