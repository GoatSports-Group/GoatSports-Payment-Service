package iuh.fit.paymentservice.presentation.controller.admin;

import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.AdminFinanceService.BulkRetryResult;
import iuh.fit.paymentservice.application.service.AdminFinanceService.FinancePage;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutFilter;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutKind;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundDetail;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundFilter;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Summary;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.shared.annotation.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tài chính toàn sàn (admin). Lọc nhiều trạng thái bằng danh sách: {@code ?status=FAILED,MANUAL_REVIEW}; tìm người
 * nhận bằng {@code people=<mã>,<mã>} (frontend tra tên/email ra mã).
 */
@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
public class FinanceAdminController {
    private final AdminFinanceService adminFinanceService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public Summary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return adminFinanceService.getSummary(from, to);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public FinancePage<PaymentRow> payments(
            @RequestParam(required = false) List<PaymentStatus> status,
            @RequestParam(required = false) List<PaymentPurpose> purpose,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminFinanceService.getPayments(status, purpose, from, to, page, size);
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public FinancePage<RefundRow> refunds(
            @RequestParam(required = false) List<RefundStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(required = false) Integer olderThanDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminFinanceService.getRefunds(new RefundFilter(status, q, people, olderThanDays), page, size);
    }

    @GetMapping("/refunds/export")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public ResponseEntity<String> exportRefunds(
            @RequestParam(required = false) List<RefundStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(required = false) Integer olderThanDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return csv("hoan-tien", adminFinanceService.exportRefunds(
                new RefundFilter(status, q, people, olderThanDays), from, to));
    }

    @GetMapping("/refunds/{refundId}")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public RefundDetail refund(@PathVariable UUID refundId) {
        return adminFinanceService.getRefund(refundId);
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public FinancePage<PayoutRow> payouts(
            @RequestParam(defaultValue = "OWNER_WITHDRAWAL") PayoutKind kind,
            @RequestParam(required = false) List<PayoutStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminFinanceService.getPayouts(new PayoutFilter(kind, status, q, people), page, size);
    }

    @GetMapping("/payouts/export")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public ResponseEntity<String> exportPayouts(
            @RequestParam(defaultValue = "OWNER_WITHDRAWAL") PayoutKind kind,
            @RequestParam(required = false) List<PayoutStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return csv(kind == PayoutKind.REFUND ? "chi-hoan" : "chu-san-rut-tien",
                adminFinanceService.exportPayouts(new PayoutFilter(kind, status, q, people), from, to));
    }

    /** Dự phòng sự cố phía nền tảng; từng khoản lỗi thì người chơi tự bấm "Thử lại". */
    @PostMapping("/refunds/retry-failed")
    @PreAuthorize("hasAuthority('RETRY_REFUND')")
    @AuditLog(action = "RETRY_FAILED_REFUNDS", description = "Admin chi lại mọi khoản hoàn đang lỗi")
    public BulkRetryResult retryFailedRefunds() {
        return adminFinanceService.retryFailedRefunds();
    }

    @PostMapping("/refunds/{refundId}/remind")
    @PreAuthorize("hasAuthority('RETRY_REFUND')")
    @AuditLog(action = "REMIND_REFUND", description = "Admin nhắc người nhận xử lý khoản hoàn #{#refundId}")
    public RefundRow remindRefund(@PathVariable UUID refundId) {
        return adminFinanceService.remindRefund(refundId, currentAdminId());
    }

    private UUID currentAdminId() {
        try {
            return currentUserProvider.getCurrentUserId().map(UUID::fromString).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Chuỗi trả thẳng (ResponseCustom không bọc String) nên client nhận đúng file CSV. */
    private static ResponseEntity<String> csv(String name, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "-" + LocalDate.now() + ".csv\"")
                .body(body);
    }
}
