package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.RefundService;
import iuh.fit.paymentservice.presentation.converter.RefundConverter;
import iuh.fit.paymentservice.presentation.request.CreateRefundRequest;
import iuh.fit.paymentservice.presentation.request.CompleteManualRefundRequest;
import iuh.fit.paymentservice.presentation.response.RefundResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class RefundInternalController {
    private final RefundService refundService;
    private final CurrentUserProvider currentUserProvider;
    private final RefundConverter refundConverter;

    @PostMapping("/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request
    ) {
        CreateRefundResult result = refundService.createRefund(
                refundConverter.toCommand(
                        paymentId,
                        authenticatedRequesterId(),
                        idempotencyKey,
                        request
                )
        );
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(refundConverter.toResponse(result.refund()));
    }

    @PostMapping("/refunds/{refundId}/manual-completion")
    public ResponseEntity<RefundResponse> completeManualRefund(
            @PathVariable UUID refundId,
            @Valid @RequestBody CompleteManualRefundRequest request
    ) {
        return ResponseEntity.ok(refundConverter.toResponse(refundService.completeManualRefund(
                CompleteManualRefundCommand.builder()
                        .refundId(refundId)
                        .completedBy(authenticatedRequesterId())
                        .providerRefundId(request.getProviderRefundId())
                        .build()
        )));
    }

    private UUID authenticatedRequesterId() {
        String requesterId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_REQUESTER_REQUIRED",
                        "Venue Service không cung cấp requester ID hợp lệ"
                ));
        try {
            return UUID.fromString(requesterId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException(
                    "AUTHENTICATED_REQUESTER_REQUIRED",
                    "Venue Service không cung cấp requester ID hợp lệ"
            );
        }
    }
}
