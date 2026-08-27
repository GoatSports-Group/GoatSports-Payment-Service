package iuh.fit.paymentservice.domain.enums;

public enum PaymentAttemptStatus {
    CREATED,
    REDIRECT_READY,
    PENDING,
    SUCCEEDED,
    FAILED,
    EXPIRED
}
