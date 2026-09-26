package iuh.fit.paymentservice.application.dto.response.refund;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một khoản hoàn tiền của người chơi, kèm khoản thanh toán gốc để hiển thị "hoàn cho việc gì". */
public record MyRefundView(
        UUID refundId,
        UUID paymentId,
        Double amount,
        RefundStatus status,
        String reason,
        String failureReason,
        LocalDateTime refundedAt,
        LocalDateTime createdAt,
        PaymentPurpose purpose,
        PaymentReferenceType referenceType,
        UUID referenceId,
        String description,
        /** Người chơi tự bấm nhận / thử lại được (chờ nhận, chờ liên kết ngân hàng, hoặc lần chi trước lỗi). */
        boolean claimable
) {
}
