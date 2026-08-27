package iuh.fit.paymentservice.application.exception;

public class PaymentConflictException extends PaymentApplicationException {
    public PaymentConflictException(String code, String message) {
        super(code, message);
    }
}
