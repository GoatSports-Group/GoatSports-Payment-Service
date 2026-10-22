package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.PaymentEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {
    private final JpaPaymentRepository jpaPaymentRepository;
    private final PaymentEntityMapper paymentEntityMapper;

    @Override
    public Payment save(Payment payment) {
        return paymentEntityMapper.toDomain(
                jpaPaymentRepository.saveAndFlush(paymentEntityMapper.toEntity(payment))
        );
    }

    @Override
    public double sumPayeeAmount(UUID payeeId, Collection<PaymentStatus> statuses,
                                 LocalDateTime paidBefore) {
        return jpaPaymentRepository.sumPayeeAmount(payeeId, statuses, paidBefore);
    }

    @Override
    public List<Payment> findRecentByPayee(UUID payeeId,
                                           Collection<PaymentStatus> statuses) {
        return jpaPaymentRepository.findTop200ByPayeeIdAndStatusInOrderByPaidAtDesc(payeeId, statuses).stream()
                .map(paymentEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        return jpaPaymentRepository.findById(paymentId).map(paymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(UUID paymentId) {
        return jpaPaymentRepository.findByIdForUpdate(paymentId).map(paymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaPaymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(paymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByReference(
            PaymentReferenceType referenceType,
            UUID referenceId,
            PaymentPurpose purpose
    ) {
        return jpaPaymentRepository.findByReferenceTypeAndReferenceIdAndPurpose(
                        referenceType,
                        referenceId,
                        purpose
                )
                .map(paymentEntityMapper::toDomain);
    }

    @Override
    public List<Payment> findByReferences(PaymentReferenceType referenceType, Set<UUID> referenceIds) {
        return jpaPaymentRepository.findByReferenceTypeAndReferenceIdInOrderByCreatedAtAsc(
                        referenceType, referenceIds
                ).stream()
                .map(paymentEntityMapper::toDomain)
                .toList();
    }
}
