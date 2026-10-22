package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.application.service.PaymentReconciliationService;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.presentation.converter.PaymentConverter;
import iuh.fit.paymentservice.presentation.request.CreatePaymentRequest;
import iuh.fit.paymentservice.presentation.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final CurrentUserProvider currentUserProvider;
    private final PaymentConverter paymentConverter;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        UUID payerUserId = authenticatedUserId();
        // Le phi giai dau do club-service tinh va khoi tao (/internal/payments/tournament-fee-checkouts);
        // cho client tu gui so tien la de nguoi choi tu dat muc phi cho minh.
        if (request.getPurpose() == PaymentPurpose.TOURNAMENT_FEE) {
            throw new InvalidPaymentException("SERVER_PRICED_PAYMENT",
                    "Lệ phí giải đấu chỉ được khởi tạo từ trang giải đấu");
        }
        CreatePaymentResult result = paymentService.createPayment(
                paymentConverter.toCommand(payerUserId, idempotencyKey, request)
        );
        HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(paymentConverter.toResponse(result.getPayment()));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        PaymentResponseCommand result = paymentService.getPayment(paymentId, authenticatedUserId());
        return ResponseEntity.ok(paymentConverter.toResponse(result));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable UUID paymentId) {
        UUID payerUserId = authenticatedUserId();
        paymentReconciliationService.cancel(
                paymentId,
                payerUserId,
                "Người dùng hủy tại màn hình thanh toán"
        );
        PaymentResponseCommand result = paymentService.getPayment(paymentId, payerUserId);
        return ResponseEntity.ok(paymentConverter.toResponse(result));
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
}
