package iuh.fit.paymentservice.presentation.controller.admin;

import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.AdminFinanceService.FinancePage;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutKind;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Summary;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.shared.annotation.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tài chính toàn sàn (admin). Lọc nhiều trạng thái bằng danh sách: {@code ?status=FAILED,MANUAL_REVIEW}. */
@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
public class FinanceAdminController {
    private final AdminFinanceService adminFinanceService;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminFinanceService.getRefunds(status, page, size);
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasAuthority('GET_PLATFORM_FINANCE')")
    public FinancePage<PayoutRow> payouts(
            @RequestParam(defaultValue = "OWNER_WITHDRAWAL") PayoutKind kind,
            @RequestParam(required = false) List<PayoutStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminFinanceService.getPayouts(kind, status, page, size);
    }

    @PostMapping("/refunds/{refundId}/retry")
    @PreAuthorize("hasAuthority('RETRY_REFUND')")
    @AuditLog(action = "RETRY_REFUND", description = "Admin chi lại khoản hoàn #{#refundId}")
    public RefundRow retryRefund(@PathVariable UUID refundId) {
        return adminFinanceService.retryRefund(refundId);
    }
}
