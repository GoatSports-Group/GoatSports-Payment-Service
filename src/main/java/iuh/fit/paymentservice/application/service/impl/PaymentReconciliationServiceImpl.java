package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.ProviderPaymentResultCommand;
import iuh.fit.paymentservice.application.dto.request.ProviderTransactionQueryCommand;
import iuh.fit.paymentservice.application.dto.response.ProviderTransactionStatus;
import iuh.fit.paymentservice.application.port.client.ProviderTransactionQueryPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentReconciliationService;
import iuh.fit.paymentservice.application.service.ProviderPaymentResultService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {
    private static final EnumSet<PaymentAttemptStatus> RECONCILABLE_STATUSES = EnumSet.of(
            PaymentAttemptStatus.REDIRECT_READY,
            PaymentAttemptStatus.PENDING
    );

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderTransactionQueryPort transactionQueryPort;
    private final ProviderPaymentResultService providerPaymentResultService;

    @Override
    public void reconcile(UUID attemptId) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || !RECONCILABLE_STATUSES.contains(attempt.getStatus())) {
            return;
        }
        Payment payment = paymentRepository.findById(attempt.getPaymentId()).orElse(null);
        if (payment == null) {
            return;
        }

        ProviderTransactionStatus status = transactionQueryPort.query(
                ProviderTransactionQueryCommand.builder()
                        .attemptId(attemptId)
                        .expectedAmount(payment.getAmount())
                        .locale("vi")
                        .build()
        );
        providerPaymentResultService.apply(ProviderPaymentResultCommand.builder()
                .attemptId(attemptId)
                .providerTransactionId(status.getProviderTransactionId())
                .amount(status.getAmount())
                .resultCode(status.getResultCode())
                .message(status.getMessage())
                .occurredAt(status.getOccurredAt())
                .build());
    }
}
