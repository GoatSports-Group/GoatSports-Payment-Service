package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.SettlementStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi trả doanh thu cho chủ sân / người tổ chức giải. Tiền khách trả nằm ở tài khoản payOS của nền tảng;
 * số dư của chủ nhận = tiền thu đã qua thời gian giữ − mọi khoản hoàn trên các khoản đó − tiền đã rút.
 */
public interface OwnerPayoutService {
    /** Dịch vụ nghiệp vụ (venue/club) xác nhận khoản thu thuộc về ai; idempotent. Tiền mặt bị bỏ qua. */
    void assignPayee(UUID paymentId, UUID payeeId);

    Balance getBalance(UUID payeeId);

    List<Withdrawal> getWithdrawals(UUID payeeId);

    List<Earning> getEarnings(UUID payeeId);

    /** Rút toàn bộ số dư khả dụng về tài khoản ngân hàng mặc định. */
    Withdrawal withdraw(UUID payeeId);

    /** Cập nhật lần rút theo kết quả payOS (gọi từ đối soát payout định kỳ). */
    void applyProviderResult(Payout payout, ProviderPayoutResult result);

    record Balance(double earned, double refunded, double withdrawn, double onHold, double available,
                   int holdDays, double minimumWithdrawal) {
    }

    /** {@code amount} la phan chu nhan duoc huong; {@code onHold} khi con trong thoi gian giu de hoan. */
    record Earning(UUID paymentId, PaymentPurpose purpose, PaymentReferenceType referenceType, UUID referenceId,
                   double amount, PaymentStatus status, LocalDateTime paidAt, boolean onHold) {
    }

    record Withdrawal(UUID settlementId, double amount, SettlementStatus status, String destinationAccount,
                      String destinationAccountName, String failureReason, LocalDateTime requestedAt,
                      LocalDateTime settledAt) {
    }
}
