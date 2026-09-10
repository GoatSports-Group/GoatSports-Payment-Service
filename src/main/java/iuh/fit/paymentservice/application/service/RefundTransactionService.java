package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.domain.entity.Refund;

import java.util.UUID;

public interface RefundTransactionService {
    PreparedRefundResult prepare(CreateRefundCommand command);

    Refund completeManualRefund(CompleteManualRefundCommand command);

    Refund findById(UUID refundId);
}
