package iuh.fit.paymentservice.domain.enums;

public enum RefundStatus {
    CREATED,
    AWAITING_CLAIM,
    AWAITING_BANK_ACCOUNT,
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    MANUAL_REVIEW
}
