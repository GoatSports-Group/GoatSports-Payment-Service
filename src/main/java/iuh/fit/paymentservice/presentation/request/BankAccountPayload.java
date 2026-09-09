package iuh.fit.paymentservice.presentation.request;

public record BankAccountPayload(
        String bankBin,
        String accountNumber,
        String accountName
) {
}
