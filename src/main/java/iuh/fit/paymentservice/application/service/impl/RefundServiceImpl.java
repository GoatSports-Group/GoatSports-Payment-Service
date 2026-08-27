package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.ProviderRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.CreateRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.mapper.RefundMapper;
import iuh.fit.paymentservice.application.port.client.PaymentRefundPort;
import iuh.fit.paymentservice.application.service.RefundService;
import iuh.fit.paymentservice.application.service.RefundTransactionService;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundTransactionService refundTransactionService;
    private final PaymentRefundPort paymentRefundPort;
    private final RefundMapper refundMapper;

    @Override
    public CreateRefundResult createRefund(CreateRefundCommand command) {
        PreparedRefundResult prepared;
        try {
            prepared = refundTransactionService.prepare(command);
        } catch (DataIntegrityViolationException exception) {
            prepared = refundTransactionService.prepare(command);
        }
        if (prepared.replayed() || prepared.provider() == PaymentProvider.MANUAL) {
            return result(prepared.refund(), prepared.replayed());
        }

        try {
            ProviderRefundResult providerResult = paymentRefundPort.refund(
                    ProviderRefundCommand.builder()
                            .refundId(prepared.refund().getRefundId())
                            .amount(prepared.refund().getAmount())
                            .providerTransactionId(prepared.providerTransactionId())
                            .description(prepared.refund().getReason())
                            .locale("vi")
                            .build()
            );
            return result(
                    refundTransactionService.complete(prepared.refund().getRefundId(), providerResult),
                    false
            );
        } catch (PaymentProviderException exception) {
            Refund refund = refundTransactionService.markManualReview(
                    prepared.refund().getRefundId(),
                    "Không xác định được kết quả từ MoMo: " + exception.getMessage()
            );
            return result(refund, false);
        } catch (RuntimeException exception) {
            Refund refund = refundTransactionService.markManualReview(
                    prepared.refund().getRefundId(),
                    "Không xác định được kết quả refund từ provider"
            );
            return result(refund, false);
        }
    }

    private CreateRefundResult result(Refund refund, boolean replayed) {
        return new CreateRefundResult(refundMapper.toRefundResponseCommand(refund), replayed);
    }
}
