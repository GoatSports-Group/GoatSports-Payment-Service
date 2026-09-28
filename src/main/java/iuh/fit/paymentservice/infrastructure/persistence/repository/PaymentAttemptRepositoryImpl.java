package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.PaymentAttemptEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

@Repository
@RequiredArgsConstructor
public class PaymentAttemptRepositoryImpl implements PaymentAttemptRepository {
    private final JpaPaymentAttemptRepository jpaPaymentAttemptRepository;
    private final PaymentAttemptEntityMapper paymentAttemptEntityMapper;

    @Override
    public PaymentAttempt save(PaymentAttempt paymentAttempt) {
        // Có id thì chép lên entity đang quản lý: merge một entity mới (payment = null) khiến Hibernate cảnh báo
        // HHH000502 vì quan hệ payment là chỉ-đọc.
        PaymentAttemptEntity entity = paymentAttempt.getAttemptId() == null
                ? paymentAttemptEntityMapper.toEntity(paymentAttempt)
                : jpaPaymentAttemptRepository.findById(paymentAttempt.getAttemptId())
                        .map(existing -> {
                            paymentAttemptEntityMapper.updateEntity(paymentAttempt, existing);
                            return existing;
                        })
                        .orElseGet(() -> paymentAttemptEntityMapper.toEntity(paymentAttempt));
        return paymentAttemptEntityMapper.toDomain(jpaPaymentAttemptRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<PaymentAttempt> findById(UUID attemptId) {
        return jpaPaymentAttemptRepository.findById(attemptId).map(paymentAttemptEntityMapper::toDomain);
    }

    @Override
    public Optional<PaymentAttempt> findByIdForUpdate(UUID attemptId) {
        return jpaPaymentAttemptRepository.findByIdForUpdate(attemptId)
                .map(paymentAttemptEntityMapper::toDomain);
    }

    @Override
    public Optional<PaymentAttempt> findByProviderAndProviderTransactionIdForUpdate(
            PaymentProvider provider,
            String providerTransactionId
    ) {
        return jpaPaymentAttemptRepository.findByProviderAndProviderTransactionIdForUpdate(
                        provider,
                        providerTransactionId
                )
                .map(paymentAttemptEntityMapper::toDomain);
    }

    @Override
    public Optional<PaymentAttempt> findLatestByPaymentIdAndStatuses(
            UUID paymentId,
            Collection<PaymentAttemptStatus> statuses
    ) {
        return jpaPaymentAttemptRepository
                .findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(paymentId, statuses)
                .map(paymentAttemptEntityMapper::toDomain);
    }

    @Override
    public Optional<PaymentAttempt> findLatestByPaymentIdAndProviderAndMethodAndStatuses(
            UUID paymentId,
            PaymentProvider provider,
            PaymentMethod method,
            Collection<PaymentAttemptStatus> statuses
    ) {
        return jpaPaymentAttemptRepository
                .findFirstByPaymentIdAndProviderAndMethodAndStatusInOrderByCreatedAtDesc(
                        paymentId,
                        provider,
                        method,
                        statuses
                )
                .map(paymentAttemptEntityMapper::toDomain);
    }

    @Override
    public List<PaymentAttempt> findLatestByPaymentIds(Collection<UUID> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) return List.of();
        return jpaPaymentAttemptRepository.findByPaymentIdInOrderByCreatedAtDesc(paymentIds).stream()
                .map(paymentAttemptEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<PaymentAttempt> findReconciliationCandidates(
            PaymentProvider provider,
            Collection<PaymentAttemptStatus> statuses,
            LocalDateTime updatedBefore,
            int limit
    ) {
        return jpaPaymentAttemptRepository
                .findByProviderAndStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        provider,
                        statuses,
                        updatedBefore,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(paymentAttemptEntityMapper::toDomain)
                .toList();
    }
}
