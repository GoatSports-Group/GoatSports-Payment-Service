package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.exception.PaymentApplicationException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.AdminFinanceQueryPort;
import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.RefundDisbursementService;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminFinanceServiceImpl implements AdminFinanceService {
    /** Giao dịch đã thu tiền; hoàn một phần/toàn bộ vẫn tính vào tiền thu (khoản hoàn tính riêng). */
    static final List<PaymentStatus> COLLECTED = List.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    static final int DEFAULT_PERIOD_DAYS = 30;
    static final int MAX_PERIOD_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 50;

    private final AdminFinanceQueryPort query;
    private final RefundDisbursementService refundDisbursementService;
    private final Clock clock = Clock.systemDefaultZone();

    @Override
    public Summary getSummary(LocalDate from, LocalDate to) {
        Period period = period(from, to);
        Totals collected = query.sumCollected(COLLECTED, period.start(), period.endExclusive());
        return new Summary(period.from(), period.to(),
                value(collected.amount()), value(collected.platformFee()), collected.count() == null ? 0 : collected.count(),
                query.sumRefunded(period.start(), period.endExclusive()),
                query.sumPaidOutToOwners(period.start(), period.endExclusive()),
                query.countRefunds(REFUNDS_NEEDING_ATTENTION),
                query.countOwnerPayouts(PAYOUTS_NEEDING_ATTENTION));
    }

    @Override
    public FinancePage<PaymentRow> getPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                               LocalDate from, LocalDate to, int page, int size) {
        Period period = period(from, to);
        return query.findPayments(orAll(statuses, PaymentStatus.class), orAll(purposes, PaymentPurpose.class),
                period.start(), period.endExclusive(), Math.max(page, 0), pageSize(size));
    }

    @Override
    public FinancePage<RefundRow> getRefunds(Collection<RefundStatus> statuses, int page, int size) {
        return query.findRefunds(orAll(statuses, RefundStatus.class), Math.max(page, 0), pageSize(size));
    }

    @Override
    public FinancePage<PayoutRow> getPayouts(PayoutKind kind, Collection<PayoutStatus> statuses, int page, int size) {
        return query.findPayouts(kind != PayoutKind.REFUND, orAll(statuses, PayoutStatus.class),
                Math.max(page, 0), pageSize(size));
    }

    @Override
    public RefundRow retryRefund(UUID refundId) {
        RefundRow refund = query.findRefund(refundId).orElseThrow(() ->
                new PaymentApplicationException("REFUND_NOT_FOUND", "Không tìm thấy khoản hoàn " + refundId));
        if (!refund.retryable()) {
            throw new PaymentConflictException("REFUND_NOT_RETRYABLE",
                    "Chỉ chi lại được khoản hoàn đang lỗi; khoản này đang ở trạng thái " + refund.status() + ".");
        }
        refundDisbursementService.retry(refundId);
        return query.findRefund(refundId).orElseThrow();
    }

    private record Period(LocalDate from, LocalDate to) {
        LocalDateTime start() {
            return from.atStartOfDay();
        }

        LocalDateTime endExclusive() {
            return to.plusDays(1).atStartOfDay();
        }
    }

    private Period period(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_PERIOD_DAYS - 1) : from;
        if (start.isAfter(end)) {
            throw new PaymentApplicationException("INVALID_PERIOD", "Ngày bắt đầu phải trước ngày kết thúc.");
        }
        if (start.plusDays(MAX_PERIOD_DAYS).isBefore(end)) {
            throw new PaymentApplicationException("INVALID_PERIOD", "Kỳ thống kê tối đa " + MAX_PERIOD_DAYS + " ngày.");
        }
        return new Period(start, end);
    }

    /** Không lọc = mọi giá trị: truy vấn luôn dùng IN (...) thay vì tham số null (PostgreSQL không suy được kiểu). */
    private static <E extends Enum<E>> Collection<E> orAll(Collection<E> values, Class<E> type) {
        return values == null || values.isEmpty() ? EnumSet.allOf(type) : values;
    }

    private static int pageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private static double value(Double amount) {
        return amount == null ? 0 : amount;
    }
}
