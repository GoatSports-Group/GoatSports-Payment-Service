package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.OwnerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Số dư doanh thu và rút tiền của chủ sân / người tổ chức giải. */
@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {
    private final OwnerPayoutService ownerPayoutService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me/balance")
    public OwnerPayoutService.Balance balance() {
        return ownerPayoutService.getBalance(authenticatedUserId());
    }

    @GetMapping("/me")
    public List<OwnerPayoutService.Withdrawal> withdrawals() {
        return ownerPayoutService.getWithdrawals(authenticatedUserId());
    }

    @GetMapping("/me/earnings")
    public List<OwnerPayoutService.Earning> earnings() {
        return ownerPayoutService.getEarnings(authenticatedUserId());
    }

    @PostMapping("/me/withdraw")
    public OwnerPayoutService.Withdrawal withdraw() {
        return ownerPayoutService.withdraw(authenticatedUserId());
    }

    private UUID authenticatedUserId() {
        String userId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_USER_REQUIRED", "Gateway không cung cấp user ID hợp lệ"
                ));
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException("AUTHENTICATED_USER_REQUIRED", "Gateway không cung cấp user ID hợp lệ");
        }
    }
}
