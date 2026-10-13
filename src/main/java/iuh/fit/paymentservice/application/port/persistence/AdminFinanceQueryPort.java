package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.application.service.AdminFinanceService.FinancePage;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Totals;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn đọc cho trang Tài chính của admin: projection và tổng hợp ở DB, không nạp entity. */
public interface AdminFinanceQueryPort {
    /** Tổng tiền thu, phí nền tảng và số giao dịch có paidAt trong [from, to). */
    Totals sumCollected(Collection<PaymentStatus> statuses, LocalDateTime from, LocalDateTime to);

    double sumRefunded(LocalDateTime from, LocalDateTime to);

    double sumPaidOutToOwners(LocalDateTime from, LocalDateTime to);

    long countRefunds(Collection<RefundStatus> statuses);

    /** Giao dịch tạo trong [from, to), mới nhất trước. */
    FinancePage<PaymentRow> findPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                         LocalDateTime from, LocalDateTime to, int page, int size);

    /**
     * Khoản hoàn tạo trong [from, to), cập nhật mới nhất trước. {@code keyword}: mẫu LIKE chữ thường, rỗng = không
     * tìm; {@code people} được coi là khớp khi có tìm.
     */
    FinancePage<RefundRow> findRefunds(Collection<RefundStatus> statuses, String keyword, Collection<UUID> people,
                                       LocalDateTime from, LocalDateTime to, int page, int size);

    Optional<RefundRow> findRefund(UUID refundId);

    Optional<PayoutRow> findRefundPayout(UUID refundId);

    long countRefundsCreatedBefore(Collection<RefundStatus> statuses, LocalDateTime before);

    /** Mã khoản hoàn ở trạng thái này, cũ nhất trước. */
    List<UUID> findRefundIds(RefundStatus status, int limit);

    /** Ghi lượt nhắc nếu lần nhắc trước đã cũ hơn {@code cutoff}; false nếu vừa nhắc gần đây. */
    boolean markReminded(UUID refundId, LocalDateTime now, LocalDateTime cutoff);

    /** Lượt chi yêu cầu trong [from, to), mới nhất trước; tìm như {@link #findRefunds}. */
    FinancePage<PayoutRow> findPayouts(boolean ownerWithdrawals, Collection<PayoutStatus> statuses, String keyword,
                                       Collection<UUID> people, LocalDateTime from, LocalDateTime to, int page, int size);
}
