package iuh.fit.paymentservice.application.dto.response.webhook;

public record VerifiedPayOsWebhook(
        Long orderCode,
        Long amount,
        String description,
        String reference,
        String transactionDateTime,
        String currency,
        String paymentLinkId,
        String code,
        String desc
) {
}
