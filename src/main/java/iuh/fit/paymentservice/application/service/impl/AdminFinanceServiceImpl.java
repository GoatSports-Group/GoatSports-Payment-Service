package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.exception.PaymentApplicationException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.AdminFinanceQueryPort;
import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.application.service.RefundDisbursementService;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFinanceServiceImpl implements AdminFinanceService {
    /** Giao dịch đã thu tiền; hoàn một phần/toàn bộ vẫn tính vào tiền thu (khoản hoàn tính riêng). */
    static final List<PaymentStatus> COLLECTED = List.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    static final int DEFAULT_PERIOD_DAYS = 30;
    static final int MAX_PERIOD_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 50;
    // ponytail: xuất tối đa 10.000 dòng một lần (kỳ tối đa 366 ngày); cần hơn thì chia kỳ hoặc chuyển sang stream.
    static final int EXPORT_LIMIT = 10_000;
    private static final int MAX_PEOPLE = 100;
    /** Danh sách hoàn tiền/chi trả không theo kỳ: lấy từ mốc này tới ngày mai. */
    private static final LocalDateTime BEGINNING = LocalDate.of(2000, 1, 1).atStartOfDay();
    private static final Set<RefundStatus> IN_FLIGHT = EnumSet.of(
            RefundStatus.CREATED, RefundStatus.PENDING, RefundStatus.PROCESSING);
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final AdminFinanceQueryPort query;
    private final RefundDisbursementService refundDisbursementService;
    private final PaymentDomainEventService paymentDomainEventService;
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
                query.countOwnerPayouts(PAYOUTS_NEEDING_ATTENTION),
                query.countRefunds(Set.of(RefundStatus.FAILED)),
                query.countRefundsCreatedBefore(REMINDABLE_REFUNDS, now().minusDays(STALE_AFTER_DAYS)));
    }

    @Override
    public FinancePage<PaymentRow> getPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                               LocalDate from, LocalDate to, int page, int size) {
        Period period = period(from, to);
        return query.findPayments(orAll(statuses, PaymentStatus.class), orAll(purposes, PaymentPurpose.class),
                period.start(), period.endExclusive(), Math.max(page, 0), pageSize(size));
    }

    @Override
    public FinancePage<RefundRow> getRefunds(RefundFilter filter, int page, int size) {
        return findRefunds(filter, BEGINNING, now().plusDays(1), Math.max(page, 0), pageSize(size));
    }

    @Override
    public RefundDetail getRefund(UUID refundId) {
        return new RefundDetail(refund(refundId), query.findRefundPayout(refundId).orElse(null));
    }

    @Override
    public FinancePage<PayoutRow> getPayouts(PayoutFilter filter, int page, int size) {
        return findPayouts(filter, BEGINNING, now().plusDays(1), Math.max(page, 0), pageSize(size));
    }

    /** Mỗi khoản chạy trong giao dịch riêng của {@code retry}: một khoản lỗi không làm hỏng các khoản còn lại. */
    @Override
    public BulkRetryResult retryFailedRefunds() {
        List<UUID> ids = query.findRefundIds(RefundStatus.FAILED, BULK_RETRY_LIMIT);
        int succeeded = 0;
        int processing = 0;
        int failed = 0;
        for (UUID id : ids) {
            try {
                refundDisbursementService.retry(id);
            } catch (RuntimeException exception) {
                log.warn("Bulk retry refund {} failed: {}", id, exception.getMessage());
            }
            RefundStatus status = query.findRefund(id).map(RefundRow::status).orElse(RefundStatus.FAILED);
            if (status == RefundStatus.SUCCEEDED) succeeded++;
            else if (IN_FLIGHT.contains(status)) processing++;
            else failed++;
        }
        return new BulkRetryResult(ids.size(), succeeded, processing, failed);
    }

    @Override
    @Transactional
    public RefundRow remindRefund(UUID refundId, UUID adminId) {
        RefundRow refund = refund(refundId);
        if (!refund.remindable()) {
            throw new PaymentConflictException("REFUND_NOT_REMINDABLE",
                    "Khoản hoàn này không chờ ai xử lý (trạng thái " + refund.status() + ").");
        }
        LocalDateTime now = now();
        if (!query.markReminded(refundId, now, now.minusHours(REMIND_COOLDOWN_HOURS))) {
            throw new PaymentConflictException("REFUND_RECENTLY_REMINDED", "Khoản này đã được nhắc lúc "
                    + (refund.lastRemindedAt() == null ? "vừa rồi" : refund.lastRemindedAt().format(SHORT_TIME))
                    + "; mỗi khoản nhắc tối đa một lần trong " + REMIND_COOLDOWN_HOURS + " giờ.");
        }
        // Cùng giao dịch với dấu thời gian nhắc (outbox): không có chuyện ghi đã nhắc mà thông báo không đi.
        paymentDomainEventService.recordRefundReminder(refund, adminId);
        return query.findRefund(refundId).orElseThrow();
    }

    @Override
    public String exportRefunds(RefundFilter filter, LocalDate from, LocalDate to) {
        Period period = period(from, to);
        List<RefundRow> rows = findRefunds(filter, period.start(), period.endExclusive(), 0, EXPORT_LIMIT).items();
        return csv(List.of("Mã khoản hoàn", "Tạo lúc", "Cập nhật", "Hoàn lúc", "Mã người nhận", "Khoản gốc",
                        "Mã thanh toán", "Mô tả khoản gốc", "Số tiền", "Trạng thái", "Lý do", "Lỗi", "Số lần tự chi lại",
                        "Mã giao dịch", "Nhắc lần cuối"),
                rows.stream().map(row -> Arrays.asList(row.refundId(), row.createdAt(), row.updatedAt(), row.refundedAt(),
                        row.payerId(), row.purpose(), row.paymentId(), row.paymentDescription(), row.amount(),
                        row.status(), row.reason(), row.failureReason(), row.retryCount(), row.providerRefundId(),
                        row.lastRemindedAt())));
    }

    @Override
    public String exportPayouts(PayoutFilter filter, LocalDate from, LocalDate to) {
        Period period = period(from, to);
        List<PayoutRow> rows = findPayouts(filter, period.start(), period.endExclusive(), 0, EXPORT_LIMIT).items();
        return csv(List.of("Mã lượt chi", "Loại", "Yêu cầu lúc", "Hoàn tất lúc", "Mã người nhận", "Chủ tài khoản",
                        "Ngân hàng (BIN)", "Số tài khoản", "Số tiền", "Trạng thái", "Mã tham chiếu", "Mã payOS",
                        "Mã lỗi", "Lỗi"),
                rows.stream().map(row -> Arrays.asList(row.payoutId(), row.kind(), row.requestedAt(), row.completedAt(),
                        row.payeeId(), row.destinationAccountName(), row.destinationBankBin(),
                        row.destinationAccountMasked(), row.amount(), row.status(), row.referenceId(),
                        row.providerPayoutId(), row.failureCode(), row.failureReason())));
    }

    private FinancePage<RefundRow> findRefunds(RefundFilter filter, LocalDateTime from, LocalDateTime to, int page, int size) {
        LocalDateTime end = filter.olderThanDays() == null || filter.olderThanDays() <= 0
                ? to : min(to, now().minusDays(filter.olderThanDays()));
        return query.findRefunds(orAll(filter.statuses(), RefundStatus.class), pattern(filter.keyword()),
                people(filter.people()), from, end, page, size);
    }

    private FinancePage<PayoutRow> findPayouts(PayoutFilter filter, LocalDateTime from, LocalDateTime to, int page, int size) {
        return query.findPayouts(filter.kind() != PayoutKind.REFUND, orAll(filter.statuses(), PayoutStatus.class),
                pattern(filter.keyword()), people(filter.people()), from, to, page, size);
    }

    private RefundRow refund(UUID refundId) {
        return query.findRefund(refundId).orElseThrow(() ->
                new PaymentApplicationException("REFUND_NOT_FOUND", "Không tìm thấy khoản hoàn " + refundId));
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Mẫu LIKE chữ thường, ký tự đại diện của người dùng được escape bằng '!' (khớp câu truy vấn); rỗng = không tìm. */
    static String pattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return "";
        String text = keyword.trim().toLowerCase(Locale.ROOT);
        if (text.length() > 100) text = text.substring(0, 100);
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static Collection<UUID> people(Collection<UUID> people) {
        return people == null ? List.of() : people.stream().filter(Objects::nonNull).distinct().limit(MAX_PEOPLE).toList();
    }

    /** UTF-8 BOM để Excel nhận đúng tiếng Việt; ô chữ bắt đầu bằng = + - @ bị chặn công thức (CSV injection). */
    static String csv(List<String> header, Stream<List<Object>> rows) {
        return "﻿" + Stream.concat(Stream.of(List.<Object>copyOf(header)), rows)
                .map(cells -> cells.stream().map(AdminFinanceServiceImpl::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    private static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof Double amount) return String.format(Locale.ROOT, "%.0f", amount);
        if (value instanceof Number number) return number.toString();
        String text = value instanceof LocalDateTime time ? time.format(CSV_TIME) : value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
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
