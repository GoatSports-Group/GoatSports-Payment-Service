package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.PaymentEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
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
    public Optional<Payment> findById(UUID paymentId) {
        return jpaPaymentRepository.findById(paymentId).map(paymentEntityMapper::toDomain);
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
}
