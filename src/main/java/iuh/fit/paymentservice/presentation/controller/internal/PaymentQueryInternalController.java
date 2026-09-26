package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.presentation.converter.PaymentConverter;
import iuh.fit.paymentservice.presentation.request.PaymentReferenceQueryRequest;
import iuh.fit.paymentservice.presentation.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentQueryInternalController {
    private final PaymentService paymentService;
    private final PaymentConverter paymentConverter;
    private final iuh.fit.paymentservice.application.service.OwnerPayoutService ownerPayoutService;

    @PostMapping("/query")
    public ResponseEntity<List<PaymentResponse>> queryByReferences(
            @Valid @RequestBody PaymentReferenceQueryRequest request
    ) {
        List<PaymentResponse> response = paymentService.getPaymentsByReferences(
                        request.getReferenceType(), request.getReferenceIds()
                ).stream()
                .map(paymentConverter::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /** venue/club báo khoản thu đã được nhận cho booking/đăng ký nào thì thuộc về chủ sân/người tổ chức nào. */
    @PostMapping("/{paymentId}/payee")
    public ResponseEntity<Void> assignPayee(@org.springframework.web.bind.annotation.PathVariable java.util.UUID paymentId,
                                            @Valid @RequestBody AssignPayeeRequest request) {
        ownerPayoutService.assignPayee(paymentId, request.payeeId());
        return ResponseEntity.noContent().build();
    }

    public record AssignPayeeRequest(@jakarta.validation.constraints.NotNull java.util.UUID payeeId) {
    }
}
