package iuh.fit.paymentservice.application.dto.request.payout;

public record CreatePayoutCommand(
        String referenceId,
        String idempotencyKey,
        Double amount,
        String bankBin,
        String accountNumber,
        String description
) {
}
