package iuh.fit.paymentservice.application.dto.event;

import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundEventPayload(
    UUID refundId,
    UUID paymentId,
    UUID requestedBy,
    String reason,
    Double amount,
    String providerRefundId,
    RefundStatus status,
    PaymentStatus paymentStatus,
    String failureReason,
    LocalDateTime refundedAt
) {
}
