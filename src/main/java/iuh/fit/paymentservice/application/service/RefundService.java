package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.RefundResponseCommand;

import java.util.UUID;

public interface RefundService {
    CreateRefundResult createRefund(CreateRefundCommand command);

    RefundResponseCommand completeManualRefund(CompleteManualRefundCommand command);

    RefundResponseCommand claim(UUID refundId, UUID payerId);
}
