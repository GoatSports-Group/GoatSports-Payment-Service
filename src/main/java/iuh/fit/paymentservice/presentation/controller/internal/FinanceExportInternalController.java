package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.service.AdminFinanceService;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutFilter;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutKind;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundFilter;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/finance")
@RequiredArgsConstructor
public class FinanceExportInternalController {
    private final AdminFinanceService adminFinanceService;

    @GetMapping("/refunds")
    public List<RefundRow> refunds(
            @RequestParam(required = false) List<RefundStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(required = false) Integer olderThanDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return adminFinanceService.exportRefunds(new RefundFilter(status, q, people, olderThanDays), from, to);
    }

    @GetMapping("/payouts")
    public List<PayoutRow> payouts(
            @RequestParam(defaultValue = "OWNER_WITHDRAWAL") PayoutKind kind,
            @RequestParam(required = false) List<PayoutStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return adminFinanceService.exportPayouts(new PayoutFilter(kind, status, q, people), from, to);
    }
}
