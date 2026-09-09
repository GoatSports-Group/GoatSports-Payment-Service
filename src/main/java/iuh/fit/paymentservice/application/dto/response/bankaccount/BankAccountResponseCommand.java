package iuh.fit.paymentservice.application.dto.response.bankaccount;

import iuh.fit.paymentservice.domain.enums.BankAccountStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record BankAccountResponseCommand(
        UUID bankAccountId,
        String bankBin,
        String accountNumberLast4,
        String accountName,
        BankAccountStatus status,
        Boolean isDefault,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt
) {
}
