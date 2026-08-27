package iuh.fit.paymentservice.application.exception;

import java.util.UUID;

public class PaymentNotFoundException extends PaymentApplicationException {
    public PaymentNotFoundException(UUID paymentId) {
        super("PAYMENT_NOT_FOUND", "Không tìm thấy payment " + paymentId);
    }
}
