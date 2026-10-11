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
import java.util.Optional;
import java.util.UUID;

/** Truy vấn đọc cho trang Tài chính của admin: projection và tổng hợp ở DB, không nạp entity. */
public interface AdminFinanceQueryPort {
    /** Tổng tiền thu, phí nền tảng và số giao dịch có paidAt trong [from, to). */
    Totals sumCollected(Collection<PaymentStatus> statuses, LocalDateTime from, LocalDateTime to);

    double sumRefunded(LocalDateTime from, LocalDateTime to);

    double sumPaidOutToOwners(LocalDateTime from, LocalDateTime to);

    long countRefunds(Collection<RefundStatus> statuses);

    long countOwnerPayouts(Collection<PayoutStatus> statuses);

    /** Giao dịch tạo trong [from, to), mới nhất trước. */
    FinancePage<PaymentRow> findPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                         LocalDateTime from, LocalDateTime to, int page, int size);

    FinancePage<RefundRow> findRefunds(Collection<RefundStatus> statuses, int page, int size);

    Optional<RefundRow> findRefund(UUID refundId);

    FinancePage<PayoutRow> findPayouts(boolean ownerWithdrawals, Collection<PayoutStatus> statuses, int page, int size);
}
