package iuh.fit.paymentservice.application.dto.request.payment;

import java.util.UUID;

public record CollectBookingCashCommand(
        UUID payerId,
        UUID bookingId,
        Double amount,
        String currency,
        UUID collectorId
) {
}
