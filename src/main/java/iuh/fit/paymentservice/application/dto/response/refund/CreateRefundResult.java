package iuh.fit.paymentservice.application.dto.response.refund;

public record CreateRefundResult(RefundResponseCommand refund, boolean replayed) {
}
