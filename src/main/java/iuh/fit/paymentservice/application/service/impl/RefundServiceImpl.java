package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.RefundResponseCommand;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.mapper.RefundMapper;
import iuh.fit.paymentservice.application.service.RefundDisbursementService;
import iuh.fit.paymentservice.application.service.RefundService;
import iuh.fit.paymentservice.application.service.RefundTransactionService;
import iuh.fit.paymentservice.domain.entity.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundTransactionService refundTransactionService;
    private final RefundMapper refundMapper;
    private final RefundDisbursementService refundDisbursementService;

    @Override
    public CreateRefundResult createRefund(CreateRefundCommand command) {
        PreparedRefundResult prepared;
        try {
            prepared = refundTransactionService.prepare(command);
        } catch (DataIntegrityViolationException exception) {
            prepared = refundTransactionService.prepare(command);
        }
        Refund refund = prepared.refund();
        if (!prepared.replayed() && Boolean.TRUE.equals(refund.getAutoDisburse())) {
            try {
                refundDisbursementService.processAutomaticRefund(refund.getRefundId());
                refund = refundTransactionService.findById(refund.getRefundId());
            } catch (PaymentConflictException exception) {
                if (!"BANK_ACCOUNT_REQUIRED".equals(exception.getCode())) throw exception;
            }
        }
        return result(refund, prepared.replayed());
    }

    @Override
    public RefundResponseCommand completeManualRefund(CompleteManualRefundCommand command) {
        return refundMapper.toRefundResponseCommand(refundTransactionService.completeManualRefund(command));
    }

    @Override
    public RefundResponseCommand claim(UUID refundId, UUID payerId) {
        return refundMapper.toRefundResponseCommand(refundDisbursementService.claim(refundId, payerId));
    }

    private CreateRefundResult result(Refund refund, boolean replayed) {
        return new CreateRefundResult(refundMapper.toRefundResponseCommand(refund), replayed);
    }
}
