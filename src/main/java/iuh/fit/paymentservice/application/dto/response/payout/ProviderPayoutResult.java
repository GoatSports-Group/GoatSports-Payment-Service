package iuh.fit.paymentservice.application.dto.response.payout;

import iuh.fit.paymentservice.domain.enums.PayoutStatus;

public record ProviderPayoutResult(
        String providerPayoutId,
        String destinationAccountName,
        PayoutStatus status,
        String failureCode,
        String failureReason
) {
}
