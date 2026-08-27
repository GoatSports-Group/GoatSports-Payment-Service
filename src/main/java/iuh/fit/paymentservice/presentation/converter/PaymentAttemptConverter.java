package iuh.fit.paymentservice.presentation.converter;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.PaymentAttemptResponseCommand;
import iuh.fit.paymentservice.presentation.request.CreatePaymentAttemptRequest;
import iuh.fit.paymentservice.presentation.response.PaymentAttemptResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentAttemptConverter {
    public CreatePaymentAttemptCommand toCommand(
            UUID paymentId,
            UUID payerId,
            String ipAddress,
            CreatePaymentAttemptRequest request
    ) {
        return CreatePaymentAttemptCommand.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .method(request.getMethod())
                .locale(request.getLocale())
                .ipAddress(ipAddress)
                .build();
    }

    public PaymentAttemptResponse toResponse(PaymentAttemptResponseCommand command) {
        return PaymentAttemptResponse.builder()
                .attemptId(command.getAttemptId())
                .paymentId(command.getPaymentId())
                .provider(command.getProvider())
                .method(command.getMethod())
                .providerTransactionId(command.getProviderTransactionId())
                .checkoutUrl(command.getCheckoutUrl())
                .status(command.getStatus())
                .failureCode(command.getFailureCode())
                .failureReason(command.getFailureReason())
                .initiatedAt(command.getInitiatedAt())
                .completedAt(command.getCompletedAt())
                .createdAt(command.getCreatedAt())
                .updatedAt(command.getUpdatedAt())
                .createdBy(command.getCreatedBy())
                .updatedBy(command.getUpdatedBy())
                .build();
    }
}
