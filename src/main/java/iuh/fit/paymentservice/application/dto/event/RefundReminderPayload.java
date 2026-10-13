package iuh.fit.paymentservice.application.dto.event;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.RefundStatus;

import java.util.UUID;

/**
 * Admin nhắc người đang giữ khoản hoàn: {@code status} MANUAL_REVIEW thì người nhận là chủ sân (venue-service tra
 * từ lượt hủy sân), còn lại là người chơi {@code payerId} (notification-service gửi).
 */
public record RefundReminderPayload(
    UUID refundId,
    UUID paymentId,
    UUID payerId,
    PaymentPurpose purpose,
    Double amount,
    RefundStatus status
) {
}
