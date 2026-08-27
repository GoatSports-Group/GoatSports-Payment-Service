package iuh.fit.paymentservice.infrastructure.scheduling;

import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.service.PaymentReconciliationService;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "payment.provider.momo",
        name = "reconciliation-enabled",
        havingValue = "true"
)
public class MomoReconciliationScheduler {
    private static final EnumSet<PaymentAttemptStatus> RECONCILABLE_STATUSES = EnumSet.of(
            PaymentAttemptStatus.REDIRECT_READY,
            PaymentAttemptStatus.PENDING
    );

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentReconciliationService paymentReconciliationService;
    private final MomoProperties properties;

    @Scheduled(fixedDelayString = "${payment.provider.momo.reconciliation-interval-ms:60000}")
    public void reconcilePendingTransactions() {
        LocalDateTime updatedBefore = LocalDateTime.now()
                .minusSeconds(properties.getReconciliationMinAgeSeconds());
        List<PaymentAttempt> candidates = paymentAttemptRepository.findReconciliationCandidates(
                PaymentProvider.MOMO,
                RECONCILABLE_STATUSES,
                updatedBefore,
                properties.getReconciliationBatchSize()
        );
        for (PaymentAttempt candidate : candidates) {
            try {
                paymentReconciliationService.reconcile(candidate.getAttemptId());
            } catch (RuntimeException exception) {
                log.warn(
                        "MoMo reconciliation failed for attempt {}: {}",
                        candidate.getAttemptId(),
                        exception.getMessage()
                );
            }
        }
    }
}
