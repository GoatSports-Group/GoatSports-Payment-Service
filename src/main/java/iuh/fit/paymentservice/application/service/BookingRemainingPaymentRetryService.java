package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.UUID;

public interface BookingRemainingPaymentRetryService {
    Payment prepare(UUID paymentId, LocalDateTime expiresAt);
}
