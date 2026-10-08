package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    /** Tổng phần chủ nhận được hưởng của các khoản đã thu, trả trước {@code paidBefore}. */
    double sumPayeeAmount(UUID payeeId, java.util.Collection<iuh.fit.paymentservice.domain.enums.PaymentStatus> statuses,
                          java.time.LocalDateTime paidBefore);

    List<Payment> findRecentByPayee(UUID payeeId,
                                    java.util.Collection<iuh.fit.paymentservice.domain.enums.PaymentStatus> statuses);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByIdForUpdate(UUID paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByReference(
            PaymentReferenceType referenceType,
            UUID referenceId,
            PaymentPurpose purpose
    );

    List<Payment> findByReferences(PaymentReferenceType referenceType, Set<UUID> referenceIds);
}
