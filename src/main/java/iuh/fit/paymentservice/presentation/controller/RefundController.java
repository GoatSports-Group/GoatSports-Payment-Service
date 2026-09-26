package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.RefundService;
import iuh.fit.paymentservice.presentation.converter.RefundConverter;
import iuh.fit.paymentservice.presentation.response.RefundResponse;
import iuh.fit.paymentservice.application.dto.response.refund.MyRefundView;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {
    private final RefundService refundService;
    private final RefundConverter refundConverter;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public List<MyRefundView> mine() {
        return refundService.getMyRefunds(authenticatedUserId());
    }

    @PostMapping("/{refundId}/claim")
    public RefundResponse claim(@PathVariable UUID refundId) {
        return refundConverter.toResponse(refundService.claim(refundId, authenticatedUserId()));
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
