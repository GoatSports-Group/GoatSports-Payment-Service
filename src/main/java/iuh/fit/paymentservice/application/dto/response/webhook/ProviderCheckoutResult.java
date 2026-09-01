package iuh.fit.paymentservice.application.dto.response.webhook;

public record ProviderCheckoutResult(
        String checkoutUrl,
        String deeplink,
        String qrCodeContent,
        String providerTransactionId
) {
}
