package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.dto.request.payment.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.CashCollectionService;
import iuh.fit.paymentservice.presentation.converter.PaymentAttemptConverter;
import iuh.fit.paymentservice.presentation.response.PaymentAttemptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class CashCollectionInternalController {
    private final CashCollectionService cashCollectionService;
    private final CurrentUserProvider currentUserProvider;
    private final PaymentAttemptConverter paymentAttemptConverter;

    @PostMapping("/{paymentId}/cash-collection")
    public ResponseEntity<PaymentAttemptResponse> collect(@PathVariable UUID paymentId) {
        CreatePaymentAttemptResult result = cashCollectionService.collect(
                CollectCashPaymentCommand.builder()
                        .paymentId(paymentId)
                        .collectorId(authenticatedCollectorId())
                        .build()
        );
        HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(paymentAttemptConverter.toResponse(result.getPaymentAttempt()));
    }

    private UUID authenticatedCollectorId() {
        String collectorId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_COLLECTOR_REQUIRED",
                        "Venue Service không cung cấp collector ID hợp lệ"
                ));
        try {
            return UUID.fromString(collectorId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException(
                    "AUTHENTICATED_COLLECTOR_REQUIRED",
                    "Venue Service không cung cấp collector ID hợp lệ"
            );
        }
    }
}
