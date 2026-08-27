package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByReferenceTypeAndReferenceIdAndPurpose(
            PaymentReferenceType referenceType,
            UUID referenceId,
            PaymentPurpose purpose
    );
}
