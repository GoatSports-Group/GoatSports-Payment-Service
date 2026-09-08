package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.RefundResponseCommand;
import iuh.fit.paymentservice.application.mapper.RefundMapper;
import iuh.fit.paymentservice.application.service.RefundService;
import iuh.fit.paymentservice.application.service.RefundTransactionService;
import iuh.fit.paymentservice.domain.entity.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundTransactionService refundTransactionService;
    private final RefundMapper refundMapper;

    @Override
    public CreateRefundResult createRefund(CreateRefundCommand command) {
        PreparedRefundResult prepared;
        try {
            prepared = refundTransactionService.prepare(command);
        } catch (DataIntegrityViolationException exception) {
            prepared = refundTransactionService.prepare(command);
        }
        return result(prepared.refund(), prepared.replayed());
    }

    @Override
    public RefundResponseCommand completeManualRefund(CompleteManualRefundCommand command) {
        return refundMapper.toRefundResponseCommand(refundTransactionService.completeManualRefund(command));
    }

    private CreateRefundResult result(Refund refund, boolean replayed) {
        return new CreateRefundResult(refundMapper.toRefundResponseCommand(refund), replayed);
    }
}
