package iuh.fit.paymentservice;

import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.AdminFinanceService.BulkRetryResult;
import iuh.fit.paymentservice.application.service.AdminFinanceService.FinancePage;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutFilter;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundFilter;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Summary;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Projection và tổng hợp của trang Tài chính admin chạy trên schema Liquibase thật (H2 chế độ PostgreSQL). */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:admin_finance_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AdminFinanceQueryTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Autowired private AdminFinanceService adminFinanceService;
    @Autowired private JdbcTemplate jdbc;

    /** Các test dùng chung một DB trong bộ nhớ và kiểm tra số đếm toàn sàn: dọn trước mỗi test. */
    @BeforeEach
    void clean() {
        jdbc.update("delete from payouts");
        jdbc.update("delete from refunds");
        jdbc.update("delete from payment_attempts");
        jdbc.update("delete from payments");
    }

    @Test
    void summarizesThePeriodAndListsAttentionRefundsWithPayerContext() {
        UUID payer = UUID.randomUUID();
        UUID paid = payment(payer, 100_000, 5_000, "SUCCEEDED", DAY.atTime(9, 0));
        UUID partlyRefunded = payment(payer, 200_000, 10_000, "PARTIALLY_REFUNDED", DAY.atTime(10, 0));
        payment(payer, 999_000, 0, "EXPIRED", null);
        payment(payer, 50_000, 0, "SUCCEEDED", DAY.minusDays(40).atTime(9, 0));
        UUID failed = refund(partlyRefunded, 80_000, "FAILED", null);
        refund(paid, 30_000, "SUCCEEDED", DAY.atTime(11, 0));
        refund(paid, 20_000, "MANUAL_REVIEW", null);

        Summary summary = adminFinanceService.getSummary(DAY.minusDays(1), DAY);

        // Hoàn một phần vẫn là tiền đã thu; giao dịch hết hạn và ngoài kỳ không tính.
        assertThat(summary.collected()).isEqualTo(300_000);
        assertThat(summary.platformFee()).isEqualTo(15_000);
        assertThat(summary.paymentCount()).isEqualTo(2);
        assertThat(summary.refunded()).isEqualTo(30_000);
        assertThat(summary.refundsNeedingAttention()).isEqualTo(2);

        FinancePage<RefundRow> attention = adminFinanceService.getRefunds(
                new RefundFilter(AdminFinanceService.REFUNDS_NEEDING_ATTENTION, null, null, null), null, null, 0, 20);
        assertThat(attention.total()).isEqualTo(2);
        RefundRow failedRow = attention.items().stream().filter(row -> row.refundId().equals(failed)).findFirst().orElseThrow();
        assertThat(failedRow.payerId()).isEqualTo(payer);
        assertThat(failedRow.remindable()).isTrue();
        assertThat(attention.items()).filteredOn(row -> row.status() == RefundStatus.MANUAL_REVIEW)
                .singleElement().satisfies(row -> assertThat(row.remindable()).isTrue());

        FinancePage<PaymentRow> succeeded = adminFinanceService.getPayments(
                List.of(PaymentStatus.SUCCEEDED), null, DAY.minusDays(60), DAY, 0, 1);
        assertThat(succeeded.total()).isEqualTo(2);
        assertThat(succeeded.items()).hasSize(1);
        assertThat(adminFinanceService.getPayouts(
                new PayoutFilter(AdminFinanceService.PayoutKind.OWNER_WITHDRAWAL, null, null, null), null, null, 0, 20)
                .total()).isZero();
    }

    @Test
    void searchesByReasonOrPersonAndFiltersRefundsWaitingTooLong() {
        UUID payer = UUID.randomUUID();
        UUID paid = payment(payer, 100_000, 0, "SUCCEEDED", DAY.atTime(9, 0));
        long staleBefore = adminFinanceService.getSummary(DAY, DAY).staleRefunds();
        UUID old = refund(paid, 10_000, "AWAITING_CLAIM", "Hủy booking GSOLD100: 100%_ưu đãi",
                LocalDateTime.now().minusDays(10));
        UUID fresh = refund(paid, 20_000, "AWAITING_CLAIM", "Hủy booking GSNEW200", LocalDateTime.now().minusHours(1));

        // Ký tự đại diện người dùng gõ (%, _) được escape: chỉ khớp đúng chuỗi.
        assertThat(ids(new RefundFilter(null, "gsold100: 100%_", null, null))).containsExactly(old);
        assertThat(ids(new RefundFilter(null, "gsold100: 100%%", null, null))).isEmpty();
        // Tìm theo người: frontend tra tên/email ra mã rồi gửi kèm từ khóa.
        assertThat(ids(new RefundFilter(null, "Nguyễn Văn A", List.of(payer), null)))
                .containsExactlyInAnyOrder(old, fresh);
        assertThat(ids(new RefundFilter(null, "gsold100", null, 7))).containsExactly(old);
        assertThat(ids(new RefundFilter(null, "gsnew200", null, 7))).isEmpty();
        assertThat(adminFinanceService.getSummary(DAY, DAY).staleRefunds()).isEqualTo(staleBefore + 1);
    }

    @Test
    void listsOnlyRefundsCreatedInTheChosenPeriodWhileTheWorkQueueCountsEveryDay() {
        UUID paid = payment(UUID.randomUUID(), 100_000, 0, "SUCCEEDED", DAY.atTime(9, 0));
        UUID inPeriod = refund(paid, 10_000, "FAILED", "Hủy sân", DAY.atTime(12, 0));
        refund(paid, 10_000, "FAILED", "Hủy sân", DAY.minusDays(45).atTime(12, 0));

        FinancePage<RefundRow> page = adminFinanceService.getRefunds(
                new RefundFilter(AdminFinanceService.REFUNDS_NEEDING_ATTENTION, null, null, null), DAY.minusDays(29), DAY, 0, 20);

        assertThat(page.items()).extracting(RefundRow::refundId).containsExactly(inPeriod);
        // Khoản kẹt từ trước kỳ vẫn nằm trong hàng đợi "Cần xử lý" (không theo kỳ).
        assertThat(adminFinanceService.getSummary(DAY.minusDays(29), DAY).refundsNeedingAttention()).isEqualTo(2);
    }

    @Test
    void remindsTheRecipientAtMostOncePerCooldownThroughTheOutbox() {
        UUID paid = payment(UUID.randomUUID(), 100_000, 0, "SUCCEEDED", DAY.atTime(9, 0));
        UUID waiting = refund(paid, 10_000, "AWAITING_BANK_ACCOUNT", "Hủy sân", LocalDateTime.now().minusDays(9));
        UUID done = refund(paid, 10_000, "SUCCEEDED", "Hủy sân", LocalDateTime.now().minusDays(9));

        RefundRow reminded = adminFinanceService.remindRefund(waiting, UUID.randomUUID());

        assertThat(reminded.lastRemindedAt()).isNotNull();
        assertThat(outboxReminders(waiting)).isEqualTo(1);
        assertThatThrownBy(() -> adminFinanceService.remindRefund(waiting, null))
                .isInstanceOf(PaymentConflictException.class).hasMessageContaining("đã được nhắc");
        assertThatThrownBy(() -> adminFinanceService.remindRefund(done, null))
                .isInstanceOf(PaymentConflictException.class);
        assertThat(outboxReminders(waiting)).isEqualTo(1);
        assertThat(adminFinanceService.getRefund(waiting).payout()).isNull();
    }

    @Test
    void bulkRetryKeepsGoingWhenARefundCannotBePaidOut() {
        UUID paid = payment(UUID.randomUUID(), 100_000, 0, "SUCCEEDED", DAY.atTime(9, 0));
        UUID failed = refund(paid, 10_000, "FAILED", "Hủy sân", LocalDateTime.now().minusDays(1));
        UUID cash = refund(paid, 10_000, "MANUAL_REVIEW", "Hủy sân", LocalDateTime.now().minusDays(1));

        // Người chơi chưa có tài khoản ngân hàng: lượt chi bị từ chối nhưng không làm hỏng cả lô.
        BulkRetryResult result = adminFinanceService.retryFailedRefunds();

        // Chỉ khoản chi lỗi; khoản tiền mặt là việc của chủ sân, không chi qua payOS.
        assertThat(result).isEqualTo(new BulkRetryResult(1, 0, 0, 1));
        assertThat(adminFinanceService.getRefund(failed).refund().status()).isEqualTo(RefundStatus.FAILED);
        assertThat(adminFinanceService.getRefund(cash).refund().status()).isEqualTo(RefundStatus.MANUAL_REVIEW);
    }

    @Test
    void exportsEveryMatchingRefundCreatedInThePeriodForTheReportService() {
        UUID paid = payment(UUID.randomUUID(), 100_000, 0, "SUCCEEDED", DAY.atTime(9, 0));
        UUID inPeriod = refund(paid, 60_000, "SUCCEEDED", "Hủy booking GSEXP300", DAY.atTime(12, 0));
        refund(paid, 10_000, "SUCCEEDED", "Hủy booking GSEXP300", DAY.minusDays(3).atTime(12, 0));

        List<RefundRow> rows = adminFinanceService.exportRefunds(new RefundFilter(null, "gsexp300", null, null), DAY, DAY);

        assertThat(rows).extracting(RefundRow::refundId).containsExactly(inPeriod);
        assertThat(rows.getFirst().paymentDescription()).isEqualTo("Đặt cọc");
    }

    private List<UUID> ids(RefundFilter filter) {
        return adminFinanceService.getRefunds(filter, null, null, 0, 50).items().stream().map(RefundRow::refundId).toList();
    }

    private Integer outboxReminders(UUID refundId) {
        return jdbc.queryForObject("select count(*) from payment_event_outbox "
                + "where event_type = 'reminder.refund-pending.v1' and payload like ?", Integer.class, "%" + refundId + "%");
    }

    private UUID payment(UUID payer, double amount, double fee, String status, LocalDateTime paidAt) {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = paidAt == null ? DAY.atTime(8, 0) : paidAt;
        jdbc.update("""
                insert into payments (payment_id, payer_id, purpose, reference_type, reference_id, amount,
                    platform_fee_amount, payee_amount, currency, description, idempotency_key, status, paid_at,
                    expires_at, created_at, updated_at, created_by, updated_by)
                values (?, ?, 'BOOKING_DEPOSIT', 'BOOKING', ?, ?, ?, ?, 'VND', 'Đặt cọc', ?, ?, ?, null, ?, ?, 'test', 'test')
                """, id, payer, UUID.randomUUID(), amount, fee, amount - fee, "pay-" + id, status, paidAt, createdAt, createdAt);
        return id;
    }

    private UUID refund(UUID paymentId, double amount, String status, LocalDateTime refundedAt) {
        return refund(paymentId, amount, status, "Hủy sân", DAY.atTime(12, 0), refundedAt);
    }

    private UUID refund(UUID paymentId, double amount, String status, String reason, LocalDateTime createdAt) {
        return refund(paymentId, amount, status, reason, createdAt, null);
    }

    private UUID refund(UUID paymentId, double amount, String status, String reason, LocalDateTime createdAt,
                        LocalDateTime refundedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into refunds (refund_id, payment_id, requested_by, reason, amount, idempotency_key,
                    auto_disburse, status, refunded_at, retry_count, created_at, updated_at, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, true, ?, ?, 6, ?, ?, 'test', 'test')
                """, id, paymentId, UUID.randomUUID(), reason, amount, "refund-" + id, status, refundedAt,
                createdAt, createdAt);
        return id;
    }
}
