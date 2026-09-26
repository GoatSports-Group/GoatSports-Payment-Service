package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.RefundResponseCommand;

import java.util.UUID;

import iuh.fit.paymentservice.application.dto.response.refund.MyRefundView;
import java.util.List;

public interface RefundService {
    CreateRefundResult createRefund(CreateRefundCommand command);

    RefundResponseCommand completeManualRefund(CompleteManualRefundCommand command);

    RefundResponseCommand claim(UUID refundId, UUID payerId);

    List<MyRefundView> getMyRefunds(UUID payerId);
}
