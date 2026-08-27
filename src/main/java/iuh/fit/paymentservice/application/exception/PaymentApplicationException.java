package iuh.fit.paymentservice.application.exception;

import lombok.Getter;

@Getter
public class PaymentApplicationException extends RuntimeException {
    private final String code;

    public PaymentApplicationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PaymentApplicationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
