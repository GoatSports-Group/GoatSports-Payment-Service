package iuh.fit.paymentservice.application.dto.request.refund;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CompleteManualRefundCommand(
        UUID refundId,
        UUID completedBy,
        String providerRefundId
) {
}
