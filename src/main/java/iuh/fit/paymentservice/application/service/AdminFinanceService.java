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
 * Tài chính toàn sàn cho admin: tiền thu, hoàn tiền, chi trả cho chủ sân. Người nhận tự gỡ khoản lỗi của mình
 * (người chơi bấm "Thử lại", chủ sân rút lại) nên admin không chi lại từng khoản; admin giám sát, nhắc khoản treo lâu,
 * chi lại hàng loạt khi lỗi do phía nền tảng (vd. tài khoản payOS hết số dư) và xuất CSV đối soát.
 */
public interface AdminFinanceService {
    /** Khoản hoàn cần người xử lý: chi lỗi (hết lượt tự chi) hoặc thanh toán tiền mặt chờ chủ sân hoàn tay. */
    Set<RefundStatus> REFUNDS_NEEDING_ATTENTION = Set.of(RefundStatus.FAILED, RefundStatus.MANUAL_REVIEW);
    Set<PayoutStatus> PAYOUTS_NEEDING_ATTENTION = Set.of(PayoutStatus.FAILED, PayoutStatus.CANCELLED);
    /** Khoản hoàn đang chờ một người (người chơi nhận / liên kết ngân hàng, chủ sân hoàn tay): nhắc được. */
    Set<RefundStatus> REMINDABLE_REFUNDS = Set.of(RefundStatus.AWAITING_CLAIM, RefundStatus.AWAITING_BANK_ACCOUNT,
            RefundStatus.FAILED, RefundStatus.MANUAL_REVIEW);
    /** Khoản nhắc được mà tạo quá chừng này ngày thì tính là "treo lâu" trên thẻ KPI. */
    int STALE_AFTER_DAYS = 7;
    /** Mỗi khoản nhắc tối đa một lần trong chừng này giờ, tránh spam người nhận. */
    int REMIND_COOLDOWN_HOURS = 24;

    /** Kỳ [from, to] theo ngày; bỏ trống thì 30 ngày gần nhất. */
    Summary getSummary(LocalDate from, LocalDate to);

    FinancePage<PaymentRow> getPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                        LocalDate from, LocalDate to, int page, int size);

    FinancePage<RefundRow> getRefunds(RefundFilter filter, int page, int size);

    RefundDetail getRefund(UUID refundId);

    FinancePage<PayoutRow> getPayouts(PayoutFilter filter, int page, int size);

    /**
     * Dự phòng cho sự cố phía nền tảng (payOS hết số dư, payOS lỗi kéo dài): job tự chi đã hết lượt và người chơi
     * không biết nền tảng đã sửa xong. Chi lại mọi khoản hoàn đang lỗi, tối đa {@link #BULK_RETRY_LIMIT} khoản cũ
     * nhất mỗi lần.
     */
    BulkRetryResult retryFailedRefunds();

    int BULK_RETRY_LIMIT = 100;

    /** Gửi lại thông báo cho người đang giữ khoản hoàn: người chơi, hoặc chủ sân với khoản tiền mặt. */
    RefundRow remindRefund(UUID refundId, UUID adminId);

    /** CSV (UTF-8 có BOM để Excel đọc đúng tiếng Việt) theo bộ lọc, trong kỳ [from, to] theo ngày tạo/yêu cầu. */
    String exportRefunds(RefundFilter filter, LocalDate from, LocalDate to);

    String exportPayouts(PayoutFilter filter, LocalDate from, LocalDate to);

    enum PayoutKind { OWNER_WITHDRAWAL, REFUND }

    /**
     * {@code keyword} khớp lý do, mô tả khoản gốc (mã đặt sân), hoặc người nhận thuộc {@code people} (frontend tra
     * tên/email ra mã người dùng, vì payment-service không giữ tên). {@code olderThanDays}: chỉ khoản tạo trước đó.
     */
    record RefundFilter(Collection<RefundStatus> statuses, String keyword, Collection<UUID> people, Integer olderThanDays) {
    }

    /** {@code keyword} khớp tên chủ tài khoản nhận, mã tham chiếu payOS, hoặc người nhận thuộc {@code people}. */
    record PayoutFilter(PayoutKind kind, Collection<PayoutStatus> statuses, String keyword, Collection<UUID> people) {
    }

    record FinancePage<T>(List<T> items, long total) {
    }

    record Summary(LocalDate from, LocalDate to, double collected, double platformFee, long paymentCount,
                   double refunded, double paidOutToOwners, long refundsNeedingAttention,
                   long payoutsNeedingAttention, long failedRefunds, long staleRefunds) {
    }

    record Totals(Double amount, Double platformFee, Long count) {
    }

    record BulkRetryResult(int attempted, int succeeded, int processing, int failed) {
    }

    record PaymentRow(UUID paymentId, UUID payerId, UUID payeeId, PaymentPurpose purpose,
                      PaymentReferenceType referenceType, UUID referenceId, Double amount, Double platformFee,
                      PaymentStatus status, String description, LocalDateTime paidAt, LocalDateTime createdAt) {
    }

    record RefundRow(UUID refundId, UUID paymentId, UUID payerId, PaymentPurpose purpose,
                     PaymentReferenceType referenceType, UUID referenceId, Double amount, RefundStatus status,
                     String reason, String failureReason, Integer retryCount, LocalDateTime createdAt,
                     LocalDateTime updatedAt, LocalDateTime refundedAt, String providerRefundId,
                     String paymentDescription, LocalDateTime lastRemindedAt, boolean remindable) {
        /** Dùng cho projection JPQL: {@code remindable} suy ra từ trạng thái, không tin client. */
        public RefundRow(UUID refundId, UUID paymentId, UUID payerId, PaymentPurpose purpose,
                         PaymentReferenceType referenceType, UUID referenceId, Double amount, RefundStatus status,
                         String reason, String failureReason, Integer retryCount, LocalDateTime createdAt,
                         LocalDateTime updatedAt, LocalDateTime refundedAt, String providerRefundId,
                         String paymentDescription, LocalDateTime lastRemindedAt) {
            this(refundId, paymentId, payerId, purpose, referenceType, referenceId, amount, status, reason,
                    failureReason, retryCount == null ? 0 : retryCount, createdAt, updatedAt, refundedAt,
                    providerRefundId, paymentDescription, lastRemindedAt, REMINDABLE_REFUNDS.contains(status));
        }
    }

    /** Khoản hoàn kèm lượt chi payOS gắn với nó (nếu đã chi lần nào). */
    record RefundDetail(RefundRow refund, PayoutRow payout) {
    }

    record PayoutRow(UUID payoutId, PayoutKind kind, UUID payeeId, Double amount, PayoutStatus status,
                     String destinationBankBin, String destinationAccountMasked, String destinationAccountName,
                     String failureCode, String failureReason, LocalDateTime requestedAt, LocalDateTime completedAt,
                     String referenceId, String providerPayoutId) {
        /** Projection JPQL: lần chi gắn settlement là chủ sân rút tiền, gắn refund là chi hoàn cho người chơi. */
        public PayoutRow(UUID payoutId, UUID settlementId, UUID payeeId, Double amount, PayoutStatus status,
                         String destinationBankBin, String destinationAccountMasked, String destinationAccountName,
                         String failureCode, String failureReason, LocalDateTime requestedAt,
                         LocalDateTime completedAt, String referenceId, String providerPayoutId) {
            this(payoutId, settlementId != null ? PayoutKind.OWNER_WITHDRAWAL : PayoutKind.REFUND, payeeId, amount,
                    status, destinationBankBin, destinationAccountMasked, destinationAccountName, failureCode,
                    failureReason, requestedAt, completedAt, referenceId, providerPayoutId);
        }
    }
}
