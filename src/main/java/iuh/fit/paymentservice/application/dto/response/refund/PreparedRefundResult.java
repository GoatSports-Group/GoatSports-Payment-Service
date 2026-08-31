package iuh.fit.paymentservice.application.dto.response.refund;

import iuh.fit.paymentservice.domain.entity.Refund;
public record PreparedRefundResult(
        Refund refund,
        boolean replayed
) {
}
