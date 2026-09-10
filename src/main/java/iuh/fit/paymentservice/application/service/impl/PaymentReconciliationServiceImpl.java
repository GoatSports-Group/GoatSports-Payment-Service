package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.response.payment.ProviderPaymentStatusResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentStatusProviderPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.application.service.PaymentReconciliationService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {
    private static final EnumSet<PaymentAttemptStatus> RECONCILABLE_ATTEMPT_STATUSES = EnumSet.of(
            PaymentAttemptStatus.REDIRECT_READY,
            PaymentAttemptStatus.PENDING
    );

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusProviderPort paymentStatusProviderPort;
    private final PaymentDomainEventService paymentDomainEventService;

    @Override
    @Transactional
    public void reconcile(UUID attemptId) {
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(attemptId).orElse(null);
        if (attempt == null || !RECONCILABLE_ATTEMPT_STATUSES.contains(attempt.getStatus())
                || attempt.getProviderTransactionId() == null
                || attempt.getProviderTransactionId().isBlank()) {
            return;
        }

        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPaymentId()).orElse(null);
        if (payment == null || payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            return;
        }

        ProviderPaymentStatusResult providerResult = paymentStatusProviderPort.get(
                attempt.getProviderTransactionId()
        );
        ensureProviderIdentityMatches(attempt, payment, providerResult);

        LocalDateTime now = LocalDateTime.now();
        if (providerResult.status() == ProviderPaymentStatusResult.Status.PENDING
                && payment.getExpiresAt() != null
                && !payment.getExpiresAt().isAfter(now)) {
            providerResult = paymentStatusProviderPort.cancel(
                    attempt.getProviderTransactionId(),
                    "GoatSports payment expired"
            );
            ensureProviderIdentityMatches(attempt, payment, providerResult);
            if (providerResult.status() == ProviderPaymentStatusResult.Status.CANCELLED) {
                providerResult = new ProviderPaymentStatusResult(
                        providerResult.providerTransactionId(), providerResult.orderCode(),
                        providerResult.amount(), providerResult.amountPaid(),
                        ProviderPaymentStatusResult.Status.EXPIRED,
                        "PAYMENT_EXPIRED", "Đã hết thời gian giữ chỗ thanh toán"
                );
            }
        }

        switch (providerResult.status()) {
            case PAID -> markSucceeded(attempt, payment, providerResult, now);
            case CANCELLED -> markTerminal(
                    attempt, payment, PaymentAttemptStatus.FAILED, PaymentStatus.CANCELLED,
                    providerResult.failureCode(), providerResult.failureReason(), now
            );
            case EXPIRED -> markTerminal(
                    attempt, payment, PaymentAttemptStatus.EXPIRED, PaymentStatus.EXPIRED,
                    providerResult.failureCode(), providerResult.failureReason(), now
            );
            case FAILED, UNDERPAID -> markTerminal(
                    attempt, payment, PaymentAttemptStatus.FAILED, PaymentStatus.FAILED,
                    providerResult.failureCode(), providerResult.failureReason(), now
            );
            case PENDING, PROCESSING -> paymentAttemptRepository.save(attempt);
        }
    }

    private void markSucceeded(
            PaymentAttempt attempt,
            Payment payment,
            ProviderPaymentStatusResult providerResult,
            LocalDateTime now
    ) {
        long expectedAmount = providerAmount(payment.getAmount());
        if (providerResult.amountPaid() == null || providerResult.amountPaid() < expectedAmount) {
            markTerminal(
                    attempt, payment, PaymentAttemptStatus.FAILED, PaymentStatus.FAILED,
                    "PAYOS_AMOUNT_MISMATCH", "Số tiền payOS ghi nhận chưa đủ", now
            );
            return;
        }
        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        attempt.setFailureCode(null);
        attempt.setFailureReason(null);
        attempt.setCompletedAt(now);
        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(now);
        Payment savedPayment = paymentRepository.save(payment);
        paymentDomainEventService.recordPaymentResult(savedPayment, savedAttempt, savedPayment.getPayerId());
        log.info("Payment {} confirmed by payOS reconciliation", savedPayment.getPaymentId());
    }

    private void markTerminal(
            PaymentAttempt attempt,
            Payment payment,
            PaymentAttemptStatus attemptStatus,
            PaymentStatus paymentStatus,
            String failureCode,
            String failureReason,
            LocalDateTime now
    ) {
        attempt.setStatus(attemptStatus);
        attempt.setFailureCode(truncate(failureCode, 64));
        attempt.setFailureReason(truncate(failureReason, 500));
        attempt.setCompletedAt(now);
        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);

        payment.setStatus(paymentStatus);
        Payment savedPayment = paymentRepository.save(payment);
        paymentDomainEventService.recordPaymentResult(savedPayment, savedAttempt, savedPayment.getPayerId());
        log.info("Payment {} reconciled as {}", savedPayment.getPaymentId(), savedPayment.getStatus());
    }

    private void ensureProviderIdentityMatches(
            PaymentAttempt attempt,
            Payment payment,
            ProviderPaymentStatusResult providerResult
    ) {
        long expectedOrderCode = PaymentAttemptServiceImpl.providerOrderCode(attempt.getAttemptId());
        long expectedAmount = providerAmount(payment.getAmount());
        if (!attempt.getProviderTransactionId().equals(providerResult.providerTransactionId())
                || providerResult.orderCode() == null
                || providerResult.orderCode() != expectedOrderCode
                || providerResult.amount() == null
                || providerResult.amount() != expectedAmount) {
            throw new PaymentProviderException(
                    "PAYOS_RECONCILIATION_MISMATCH",
                    "Dữ liệu đối soát payOS không khớp payment"
            );
        }
    }

    private long providerAmount(Double amount) {
        try {
            return BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(
                    "PAYOS_INVALID_AMOUNT",
                    "Số tiền payment không hợp lệ để đối soát payOS",
                    exception
            );
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
