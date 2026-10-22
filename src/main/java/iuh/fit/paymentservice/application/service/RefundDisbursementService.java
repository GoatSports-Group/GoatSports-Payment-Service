package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.domain.entity.Refund;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RefundDisbursementService {
    /** Khoản hoàn đang kẹt (lỗi phía payOS hoặc chờ tài khoản) đến lượt tự chi lại theo giãn cách. */
    List<UUID> findRetryableRefundIds(LocalDateTime now);

    /** Tăng bộ đếm trong transaction riêng (lỗi giữa chừng vẫn giữ được số lần đã thử); false nếu hết lượt. */
    boolean recordRetryAttempt(UUID refundId);

    /** Chi lại một khoản hoàn nếu người nhận đang có tài khoản ngân hàng dùng được. */
    void retry(UUID refundId);

    Refund claim(UUID refundId, UUID payerId);

    void processAutomaticRefund(UUID refundId);

    void processWaitingAutomaticRefunds(UUID payerId);

    void reconcileProcessingPayouts();
}
