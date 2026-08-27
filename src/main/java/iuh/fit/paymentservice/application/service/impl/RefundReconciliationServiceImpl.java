package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;
import iuh.fit.paymentservice.application.port.client.ProviderRefundQueryPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.service.RefundReconciliationService;
import iuh.fit.paymentservice.application.service.RefundTransactionService;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundReconciliationServiceImpl implements RefundReconciliationService {
    private static final EnumSet<RefundStatus> RECONCILABLE_STATUSES = EnumSet.of(
            RefundStatus.PENDING,
            RefundStatus.MANUAL_REVIEW
    );
    private static final EnumSet<PaymentAttemptStatus> SUCCEEDED_ATTEMPT_STATUS =
            EnumSet.of(PaymentAttemptStatus.SUCCEEDED);

    private final RefundRepository refundRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProviderRefundQueryPort refundQueryPort;
    private final RefundTransactionService refundTransactionService;

    @Override
    public void reconcile(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElse(null);
        if (refund == null || !RECONCILABLE_STATUSES.contains(refund.getStatus())) {
            return;
        }
        PaymentAttempt successfulAttempt = paymentAttemptRepository
                .findLatestByPaymentIdAndStatuses(refund.getPaymentId(), SUCCEEDED_ATTEMPT_STATUS)
                .orElse(null);
        if (successfulAttempt == null || successfulAttempt.getProvider() != PaymentProvider.MOMO) {
            return;
        }
        ProviderRefundResult result = refundQueryPort.query(refund.getRefundId());
        refundTransactionService.complete(refund.getRefundId(), result);
    }
}
