package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.presentation.converter.PaymentAttemptConverter;
import iuh.fit.paymentservice.presentation.request.CreatePaymentAttemptRequest;
import iuh.fit.paymentservice.presentation.response.PaymentAttemptResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/attempts")
@RequiredArgsConstructor
public class PaymentAttemptController {
    private final PaymentAttemptService paymentAttemptService;
    private final CurrentUserProvider currentUserProvider;
    private final PaymentAttemptConverter paymentAttemptConverter;

    @PostMapping
    public ResponseEntity<PaymentAttemptResponse> createPaymentAttempt(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CreatePaymentAttemptRequest request,
            HttpServletRequest httpRequest
    ) {
        CreatePaymentAttemptResult result = paymentAttemptService.createPaymentAttempt(
                paymentAttemptConverter.toCommand(
                        paymentId,
                        authenticatedUserId(),
                        clientIp(httpRequest),
                        request
                )
        );
        HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(paymentAttemptConverter.toResponse(result.getPaymentAttempt()));
    }

    private UUID authenticatedUserId() {
        String userId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_USER_REQUIRED",
                        "Gateway không cung cấp user ID hợp lệ"
                ));
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException(
                    "AUTHENTICATED_USER_REQUIRED",
                    "Gateway không cung cấp user ID hợp lệ"
            );
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
