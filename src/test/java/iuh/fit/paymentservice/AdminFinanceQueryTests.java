package iuh.fit.paymentservice;

import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.AdminFinanceService.FinancePage;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Summary;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
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

        FinancePage<RefundRow> attention = adminFinanceService.getRefunds(AdminFinanceService.REFUNDS_NEEDING_ATTENTION, 0, 20);
        assertThat(attention.total()).isEqualTo(2);
        RefundRow failedRow = attention.items().stream().filter(row -> row.refundId().equals(failed)).findFirst().orElseThrow();
        assertThat(failedRow.payerId()).isEqualTo(payer);
        assertThat(failedRow.retryable()).isTrue();
        assertThat(attention.items()).filteredOn(row -> row.status() == RefundStatus.MANUAL_REVIEW)
                .singleElement().satisfies(row -> assertThat(row.retryable()).isFalse());

        FinancePage<PaymentRow> succeeded = adminFinanceService.getPayments(
                List.of(PaymentStatus.SUCCEEDED), null, DAY.minusDays(60), DAY, 0, 1);
        assertThat(succeeded.total()).isEqualTo(2);
        assertThat(succeeded.items()).hasSize(1);
        assertThat(adminFinanceService.getPayouts(AdminFinanceService.PayoutKind.OWNER_WITHDRAWAL, null, 0, 20).total())
                .isZero();
    }

    @Test
    void refusesToRetryARefundThatIsNotFailed() {
        UUID paid = payment(UUID.randomUUID(), 100_000, 0, "SUCCEEDED", DAY.atTime(9, 0));
        UUID manual = refund(paid, 10_000, "MANUAL_REVIEW", null);

        assertThatThrownBy(() -> adminFinanceService.retryRefund(manual)).isInstanceOf(PaymentConflictException.class);
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
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into refunds (refund_id, payment_id, requested_by, reason, amount, idempotency_key,
                    auto_disburse, status, refunded_at, retry_count, created_at, updated_at, created_by, updated_by)
                values (?, ?, ?, 'Hủy sân', ?, ?, true, ?, ?, 6, ?, ?, 'test', 'test')
                """, id, paymentId, UUID.randomUUID(), amount, "refund-" + id, status, refundedAt,
                DAY.atTime(12, 0), DAY.atTime(12, 0));
        return id;
    }
}
