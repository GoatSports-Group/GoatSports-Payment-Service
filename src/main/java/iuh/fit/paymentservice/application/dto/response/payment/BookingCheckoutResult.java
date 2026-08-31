package iuh.fit.paymentservice.application.dto.response.payment;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookingCheckoutResult(
        UUID paymentId,
        String status,
        String checkoutUrl,
        String deeplink,
        String qrCodeContent,
        LocalDateTime expiresAt
) {
}
