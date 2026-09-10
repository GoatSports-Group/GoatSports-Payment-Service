package iuh.fit.paymentservice.application.dto.request.payment;

import iuh.fit.paymentservice.application.exception.PaymentConflictException;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateBookingCheckoutCommand(
        UUID payerId,
        UUID bookingId,
        Double amount,
        String currency,
        String locale,
        String ipAddress,
        LocalDateTime expiresAt
) {
    public void validate() {
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            throw new PaymentConflictException(
                    "BOOKING_HOLD_EXPIRED", "Thời gian giữ chỗ của booking đã hết"
            );
        }
    }
}
