package iuh.fit.paymentservice.application.dto.request.bankaccount;

import java.util.UUID;

public record LinkBankAccountCommand(
        UUID payeeId,
        String bankBin,
        String accountNumber,
        String accountName
) {
}
