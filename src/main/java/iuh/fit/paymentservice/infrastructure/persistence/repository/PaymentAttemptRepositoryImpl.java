package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.PaymentAttemptEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentAttemptRepositoryImpl implements PaymentAttemptRepository {
    private final JpaPaymentAttemptRepository jpaPaymentAttemptRepository;
    private final PaymentAttemptEntityMapper paymentAttemptEntityMapper;

    @Override
    public PaymentAttempt save(PaymentAttempt paymentAttempt) {
        return paymentAttemptEntityMapper.toDomain(
                jpaPaymentAttemptRepository.saveAndFlush(paymentAttemptEntityMapper.toEntity(paymentAttempt))
        );
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
}
