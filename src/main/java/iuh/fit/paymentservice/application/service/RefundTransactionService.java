package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;
import iuh.fit.paymentservice.domain.entity.Refund;

import java.util.UUID;

public interface RefundTransactionService {
    PreparedRefundResult prepare(CreateRefundCommand command);

    Refund complete(UUID refundId, ProviderRefundResult providerResult);

    Refund markManualReview(UUID refundId, String reason);
}
