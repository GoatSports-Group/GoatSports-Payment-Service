package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;

public interface RefundService {
    CreateRefundResult createRefund(CreateRefundCommand command);
}
