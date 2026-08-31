package iuh.fit.paymentservice.application.dto.response.attempt;

public record ProviderCheckoutResult(
        String checkoutUrl,
        String deeplink,
        String qrCodeContent
) {
}
