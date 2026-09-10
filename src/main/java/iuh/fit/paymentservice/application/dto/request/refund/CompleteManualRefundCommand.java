package iuh.fit.paymentservice.application.dto.request.refund;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CompleteManualRefundCommand(
        UUID refundId,
        UUID completedBy,
        String providerRefundId
) {
    public void validate() {
        if (refundId == null || completedBy == null
                || providerRefundId == null || providerRefundId.isBlank()
                || providerRefundId.trim().length() > 100) {
            throw new InvalidPaymentException(
                    "MANUAL_REFUND_COMPLETION_INVALID",
                    "Refund ID, người xác nhận và mã giao dịch hoàn tiền hợp lệ là bắt buộc"
            );
        }
    }
}
