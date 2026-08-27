package iuh.fit.paymentservice.application.exception;

public class PaymentProviderException extends PaymentApplicationException {
    public PaymentProviderException(String code, String message) {
        super(code, message);
    }

    public PaymentProviderException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
