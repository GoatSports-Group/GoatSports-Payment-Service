package iuh.fit.paymentservice.infrastructure.scheduling;

import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.service.PaymentReconciliationService;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "payment.reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentReconciliationJob {
    private static final EnumSet<PaymentAttemptStatus> ACTIVE_STATUSES = EnumSet.of(
            PaymentAttemptStatus.REDIRECT_READY,
            PaymentAttemptStatus.PENDING
    );

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentReconciliationService paymentReconciliationService;

    @Value("${payment.reconciliation.stale-after-seconds}")
    private long staleAfterSeconds;

    @Value("${payment.reconciliation.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payment.reconciliation.delay-ms}")
    public void reconcilePendingPayments() {
        List<PaymentAttempt> candidates = paymentAttemptRepository.findReconciliationCandidates(
                PaymentProvider.PAYOS,
                ACTIVE_STATUSES,
                LocalDateTime.now().minusSeconds(Math.max(0, staleAfterSeconds)),
                Math.max(1, batchSize)
        );
        for (PaymentAttempt candidate : candidates) {
            try {
                paymentReconciliationService.reconcile(candidate.getAttemptId());
            } catch (RuntimeException exception) {
                log.warn(
                        "Unable to reconcile payment attempt {}: {}",
                        candidate.getAttemptId(), exception.getMessage()
                );
            }
        }
    }
}
