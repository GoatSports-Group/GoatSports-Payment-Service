package iuh.fit.paymentservice.presentation.converter;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.RefundResponseCommand;
import iuh.fit.paymentservice.presentation.request.CreateRefundRequest;
import iuh.fit.paymentservice.presentation.response.RefundResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RefundConverter {
    public CreateRefundCommand toCommand(
            UUID paymentId,
            UUID requestedBy,
            String idempotencyKey,
            CreateRefundRequest request
    ) {
        return CreateRefundCommand.builder()
                .paymentId(paymentId)
                .requestedBy(requestedBy)
                .reason(request.getReason())
                .amount(request.getAmount())
                .autoDisburse(request.getAutoDisburse())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    public RefundResponse toResponse(RefundResponseCommand command) {
        return RefundResponse.builder()
                .refundId(command.getRefundId())
                .paymentId(command.getPaymentId())
                .requestedBy(command.getRequestedBy())
                .reason(command.getReason())
                .amount(command.getAmount())
                .providerRefundId(command.getProviderRefundId())
                .idempotencyKey(command.getIdempotencyKey())
                .autoDisburse(command.getAutoDisburse())
                .status(command.getStatus())
                .failureReason(command.getFailureReason())
                .refundedAt(command.getRefundedAt())
                .createdAt(command.getCreatedAt())
                .updatedAt(command.getUpdatedAt())
                .createdBy(command.getCreatedBy())
                .updatedBy(command.getUpdatedBy())
                .build();
    }
}
