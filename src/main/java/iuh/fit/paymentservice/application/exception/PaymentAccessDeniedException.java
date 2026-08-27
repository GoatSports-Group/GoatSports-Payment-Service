package iuh.fit.paymentservice.application.exception;

public class PaymentAccessDeniedException extends PaymentApplicationException {
    public PaymentAccessDeniedException() {
        super("PAYMENT_ACCESS_DENIED", "Bạn không có quyền truy cập payment này");
    }
}
