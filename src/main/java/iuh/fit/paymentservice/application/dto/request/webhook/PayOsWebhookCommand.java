package iuh.fit.paymentservice.application.dto.request.webhook;

public record PayOsWebhookCommand(
        String code,
        String desc,
        Boolean success,
        Data data,
        String signature
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
