package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.service.OwnerPayoutService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.presentation.converter.PaymentConverter;
import iuh.fit.paymentservice.presentation.request.PaymentReferenceQueryRequest;
import iuh.fit.paymentservice.presentation.response.PaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentQueryInternalController {
    private final PaymentService paymentService;
    private final PaymentConverter paymentConverter;
    private final OwnerPayoutService ownerPayoutService;

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
    public ResponseEntity<Void> assignPayee(@PathVariable UUID paymentId,
                                            @Valid @RequestBody AssignPayeeRequest request) {
        ownerPayoutService.assignPayee(paymentId, request.payeeId());
        return ResponseEntity.noContent().build();
    }

    public record AssignPayeeRequest(@NotNull UUID payeeId) {
    }
}
