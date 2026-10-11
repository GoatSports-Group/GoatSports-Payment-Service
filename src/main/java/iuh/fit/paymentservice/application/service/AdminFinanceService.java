package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tài chính toàn sàn cho admin: tiền thu, hoàn tiền, chi trả cho chủ sân. Chỉ đọc, trừ một thao tác vận hành:
 * chi lại khoản hoàn đã hết lượt tự chi (trước đây chỉ người chơi tự bấm "Thử lại" được).
 */
public interface AdminFinanceService {
    /** Khoản hoàn cần người xử lý: chi lỗi (hết lượt tự chi) hoặc thanh toán tiền mặt chờ chủ sân hoàn tay. */
    Set<RefundStatus> REFUNDS_NEEDING_ATTENTION = Set.of(RefundStatus.FAILED, RefundStatus.MANUAL_REVIEW);
    Set<PayoutStatus> PAYOUTS_NEEDING_ATTENTION = Set.of(PayoutStatus.FAILED, PayoutStatus.MANUAL_REVIEW);

    /** Kỳ [from, to] theo ngày; bỏ trống thì 30 ngày gần nhất. */
    Summary getSummary(LocalDate from, LocalDate to);

    FinancePage<PaymentRow> getPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                        LocalDate from, LocalDate to, int page, int size);

    FinancePage<RefundRow> getRefunds(Collection<RefundStatus> statuses, int page, int size);

    FinancePage<PayoutRow> getPayouts(PayoutKind kind, Collection<PayoutStatus> statuses, int page, int size);

    /** Chi lại ngay khoản hoàn đang lỗi; người chơi chưa có tài khoản ngân hàng dùng được thì báo lỗi. */
    RefundRow retryRefund(UUID refundId);

    enum PayoutKind { OWNER_WITHDRAWAL, REFUND }

    record FinancePage<T>(List<T> items, long total) {
    }

    record Summary(LocalDate from, LocalDate to, double collected, double platformFee, long paymentCount,
                   double refunded, double paidOutToOwners, long refundsNeedingAttention,
                   long payoutsNeedingAttention) {
    }

    record Totals(Double amount, Double platformFee, Long count) {
    }

    record PaymentRow(UUID paymentId, UUID payerId, UUID payeeId, PaymentPurpose purpose,
                      PaymentReferenceType referenceType, UUID referenceId, Double amount, Double platformFee,
                      PaymentStatus status, String description, LocalDateTime paidAt, LocalDateTime createdAt) {
    }

    record RefundRow(UUID refundId, UUID paymentId, UUID payerId, PaymentPurpose purpose,
                     PaymentReferenceType referenceType, UUID referenceId, Double amount, RefundStatus status,
                     String reason, String failureReason, Integer retryCount, LocalDateTime createdAt,
                     LocalDateTime updatedAt, LocalDateTime refundedAt, boolean retryable) {
        /** Dùng cho projection JPQL: {@code retryable} suy ra từ trạng thái, không tin client. */
        public RefundRow(UUID refundId, UUID paymentId, UUID payerId, PaymentPurpose purpose,
                         PaymentReferenceType referenceType, UUID referenceId, Double amount, RefundStatus status,
                         String reason, String failureReason, Integer retryCount, LocalDateTime createdAt,
                         LocalDateTime updatedAt, LocalDateTime refundedAt) {
            this(refundId, paymentId, payerId, purpose, referenceType, referenceId, amount, status, reason,
                    failureReason, retryCount == null ? 0 : retryCount, createdAt, updatedAt, refundedAt,
                    status == RefundStatus.FAILED);
        }
    }

    record PayoutRow(UUID payoutId, PayoutKind kind, UUID payeeId, Double amount, PayoutStatus status,
                     String destinationBankBin, String destinationAccountMasked, String destinationAccountName,
                     String failureCode, String failureReason, LocalDateTime requestedAt, LocalDateTime completedAt) {
        /** Projection JPQL: lần chi gắn settlement là chủ sân rút tiền, gắn refund là chi hoàn cho người chơi. */
        public PayoutRow(UUID payoutId, UUID settlementId, UUID payeeId, Double amount, PayoutStatus status,
                         String destinationBankBin, String destinationAccountMasked, String destinationAccountName,
                         String failureCode, String failureReason, LocalDateTime requestedAt,
                         LocalDateTime completedAt) {
            this(payoutId, settlementId != null ? PayoutKind.OWNER_WITHDRAWAL : PayoutKind.REFUND, payeeId, amount,
                    status, destinationBankBin, destinationAccountMasked, destinationAccountName, failureCode,
                    failureReason, requestedAt, completedAt);
        }
    }
}
