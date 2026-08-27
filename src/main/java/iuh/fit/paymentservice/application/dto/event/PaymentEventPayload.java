package iuh.fit.paymentservice.application.dto.event;

import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentEventPayload(
    UUID paymentId,
    UUID payerId,
    PaymentPurpose purpose,
    PaymentReferenceType referenceType,
    UUID referenceId,
    Double amount,
    String currency,
    PaymentStatus status,
    LocalDateTime paidAt,
    PaymentProvider provider,
    PaymentMethod method,
    String providerTransactionId,
    String failureCode,
    String failureReason
) {
}
