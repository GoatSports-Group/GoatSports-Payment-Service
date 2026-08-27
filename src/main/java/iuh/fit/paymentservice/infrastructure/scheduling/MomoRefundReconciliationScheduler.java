package iuh.fit.paymentservice.infrastructure.scheduling;

import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.service.RefundReconciliationService;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
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
public class MomoRefundReconciliationScheduler {
    private static final EnumSet<RefundStatus> RECONCILABLE_STATUSES = EnumSet.of(
            RefundStatus.PENDING,
            RefundStatus.MANUAL_REVIEW
    );

    private final RefundRepository refundRepository;
    private final RefundReconciliationService refundReconciliationService;
    private final MomoProperties properties;

    @Scheduled(fixedDelayString = "${payment.provider.momo.reconciliation-interval-ms}")
    public void reconcilePendingRefunds() {
        LocalDateTime updatedBefore = LocalDateTime.now()
                .minusSeconds(properties.getReconciliationMinAgeSeconds());
        List<Refund> candidates = refundRepository.findReconciliationCandidates(
                RECONCILABLE_STATUSES,
                updatedBefore,
                properties.getReconciliationBatchSize()
        );
        for (Refund candidate : candidates) {
            try {
                refundReconciliationService.reconcile(candidate.getRefundId());
            } catch (RuntimeException exception) {
                log.warn("MoMo refund reconciliation failed for refund {}: {}",
                        candidate.getRefundId(), exception.getMessage());
            }
        }
    }
}
