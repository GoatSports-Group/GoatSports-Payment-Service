package iuh.fit.paymentservice.presentation.converter;

import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.presentation.request.CreatePaymentRequest;
import iuh.fit.paymentservice.presentation.response.PaymentResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentConverter {

    public CreatePaymentCommand toCommand(
            UUID payerId,
            String idempotencyKey,
            CreatePaymentRequest request
    ) {
        return CreatePaymentCommand.builder()
                .payerId(payerId)
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .purpose(request.getPurpose())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .expiresAt(request.getExpiresAt())
                .build();
    }

    public PaymentResponse toResponse(PaymentResponseCommand command) {
        return PaymentResponse.builder()
                .paymentId(command.getPaymentId())
                .payerId(command.getPayerId())
                .purpose(command.getPurpose())
                .referenceType(command.getReferenceType())
                .referenceId(command.getReferenceId())
                .amount(command.getAmount())
                .currency(command.getCurrency())
                .description(command.getDescription())
                .idempotencyKey(command.getIdempotencyKey())
                .status(command.getStatus())
                .provider(command.getProvider())
                .method(command.getMethod())
                .providerTransactionId(command.getProviderTransactionId())
                .failureCode(command.getFailureCode())
                .failureReason(command.getFailureReason())
                .paidAt(command.getPaidAt())
                .expiresAt(command.getExpiresAt())
                .createdAt(command.getCreatedAt())
                .updatedAt(command.getUpdatedAt())
                .createdBy(command.getCreatedBy())
                .updatedBy(command.getUpdatedBy())
                .build();
    }
}
