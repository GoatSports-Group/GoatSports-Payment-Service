package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Checkout le phi giai dau do club-service khoi tao: so tien va han thanh toan do server tinh,
 * nguoi choi khong gui duoc muc phi. Goi lai trong han se tai su dung payment cu va tao ma moi.
 */
public interface TournamentFeeCheckoutService {
    BookingCheckoutResult create(Command command);

    record Command(
            UUID payerId,
            UUID registrationId,
            Double amount,
            String description,
            String locale,
            String ipAddress,
            LocalDateTime expiresAt
    ) { }
}
