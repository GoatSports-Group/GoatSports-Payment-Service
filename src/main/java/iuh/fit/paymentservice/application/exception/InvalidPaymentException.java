package iuh.fit.paymentservice.application.exception;

public class InvalidPaymentException extends PaymentApplicationException {
    public InvalidPaymentException(String code, String message) {
        super(code, message);
    }
}
