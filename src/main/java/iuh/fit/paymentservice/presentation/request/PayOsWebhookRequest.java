package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PayOsWebhookRequest(
        String code,
        String desc,
        Boolean success,
        @NotNull @Valid Data data,
        @NotBlank String signature
) {
    public record Data(
            Long orderCode,
            Long amount,
            String description,
            String accountNumber,
            String reference,
            String transactionDateTime,
            String currency,
            String paymentLinkId,
            String code,
            String desc,
            String counterAccountBankId,
            String counterAccountBankName,
            String counterAccountName,
            String counterAccountNumber,
            String virtualAccountName,
            String virtualAccountNumber
    ) {
    }
}
