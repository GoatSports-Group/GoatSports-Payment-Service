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
}
