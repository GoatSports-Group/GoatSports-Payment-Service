package iuh.fit.paymentservice.application.dto.request.payment;

import java.util.UUID;

public record CreateBookingCheckoutCommand(
        UUID payerId,
        UUID bookingId,
        Double amount,
        String currency,
        String locale,
        String ipAddress
) {
}
