package iuh.fit.paymentservice.application.dto.response.payment;

public record ProviderPaymentStatusResult(
        String providerTransactionId,
        Long orderCode,
        Long amount,
        Long amountPaid,
        Status status,
        String failureCode,
        String failureReason
) {
    public enum Status {
        PENDING,
        PROCESSING,
        PAID,
        CANCELLED,
        EXPIRED,
        FAILED,
        UNDERPAID
    }
}
