package iuh.fit.paymentservice.application.dto.response.refund;

import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;

public record PreparedRefundResult(
        Refund refund,
        PaymentProvider provider,
        String providerTransactionId,
        boolean replayed
) {
}
