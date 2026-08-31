package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.ProviderPaymentResultCommand;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.ProviderPaymentResultService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class ProviderPaymentResultServiceImpl implements ProviderPaymentResultService {
    private static final EnumSet<PaymentStatus> NON_PAYABLE_STATUSES = EnumSet.of(
            PaymentStatus.CANCELLED,
            PaymentStatus.EXPIRED,
            PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED
    );

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDomainEventService paymentDomainEventService;

    @Override
    @Transactional(noRollbackFor = PaymentProviderException.class)
    public boolean apply(ProviderPaymentResultCommand command) {
        validateCommand(command);
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(command.getAttemptId())
                .orElseThrow(() -> providerError(
                        "MOMO_ATTEMPT_NOT_FOUND",
                        "Không tìm thấy payment attempt của callback MoMo"
                ));
        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPaymentId())
                .orElseThrow(() -> providerError(
                        "MOMO_PAYMENT_NOT_FOUND",
                        "Không tìm thấy payment của callback MoMo"
                ));

        validateProviderAndAmount(attempt, payment, command);
        if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return false;
        }
        if (NON_PAYABLE_STATUSES.contains(payment.getStatus())) {
            return false;
        }

        validateTransactionIdConsistency(attempt, command.getProviderTransactionId());
        if (isSuccess(command.getResultCode())) {
            applySuccess(attempt, payment, command);
        } else if (isPending(command.getResultCode())) {
            applyPending(attempt, payment, command);
        } else {
            applyFailure(attempt, payment, command);
        }
        paymentAttemptRepository.save(attempt);
        paymentRepository.save(payment);
        paymentDomainEventService.recordPaymentResult(payment, attempt, payment.getPayerId());
        return true;
    }

    private void applySuccess(
            PaymentAttempt attempt,
            Payment payment,
            ProviderPaymentResultCommand command
    ) {
        if (command.getProviderTransactionId() == null
                || command.getProviderTransactionId().isBlank()
                || "0".equals(command.getProviderTransactionId())) {
            throw providerError("MOMO_TRANSACTION_ID_REQUIRED", "MoMo không trả về transId hợp lệ");
        }
        LocalDateTime occurredAt = occurredAt(command);
        attempt.setProviderTransactionId(command.getProviderTransactionId());
        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        attempt.setFailureCode(null);
        attempt.setFailureReason(null);
        attempt.setCompletedAt(occurredAt);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(occurredAt);
    }

    private void applyPending(
            PaymentAttempt attempt,
            Payment payment,
            ProviderPaymentResultCommand command
    ) {
        if (command.getProviderTransactionId() != null
                && !command.getProviderTransactionId().isBlank()
                && !"0".equals(command.getProviderTransactionId())) {
            attempt.setProviderTransactionId(command.getProviderTransactionId());
        }
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        attempt.setFailureCode(null);
        attempt.setFailureReason(null);
        attempt.setCompletedAt(null);
        payment.setStatus(PaymentStatus.PENDING);
    }

    private void applyFailure(
            PaymentAttempt attempt,
            Payment payment,
            ProviderPaymentResultCommand command
    ) {
        if (command.getProviderTransactionId() != null
                && !command.getProviderTransactionId().isBlank()
                && !"0".equals(command.getProviderTransactionId())) {
            attempt.setProviderTransactionId(command.getProviderTransactionId());
        }
        attempt.setStatus(isExpired(command.getResultCode())
                ? PaymentAttemptStatus.EXPIRED
                : PaymentAttemptStatus.FAILED);
        attempt.setFailureCode(truncate("MOMO_RESULT_" + command.getResultCode(), 64));
        attempt.setFailureReason(truncate(command.getMessage(), 500));
        attempt.setCompletedAt(occurredAt(command));
        payment.setStatus(PaymentStatus.FAILED);
    }

    private void validateCommand(ProviderPaymentResultCommand command) {
        if (command == null || command.getAttemptId() == null || command.getResultCode() == null) {
            throw providerError("MOMO_RESULT_INVALID", "Kết quả giao dịch MoMo thiếu dữ liệu bắt buộc");
        }
    }

    private void validateProviderAndAmount(
            PaymentAttempt attempt,
            Payment payment,
            ProviderPaymentResultCommand command
    ) {
        if (attempt.getProvider() != PaymentProvider.MOMO) {
            throw providerError("MOMO_PROVIDER_MISMATCH", "Payment attempt không thuộc provider MoMo");
        }
        if (command.getAmount() == null || providerAmount(payment.getAmount()) != command.getAmount()) {
            throw providerError("MOMO_AMOUNT_MISMATCH", "Số tiền MoMo không khớp với payment");
        }
    }

    private void validateTransactionIdConsistency(PaymentAttempt attempt, String transactionId) {
        if (attempt.getProviderTransactionId() != null
                && transactionId != null
                && !transactionId.isBlank()
                && !"0".equals(transactionId)
                && !attempt.getProviderTransactionId().equals(transactionId)) {
            throw providerError(
                    "MOMO_TRANSACTION_MISMATCH",
                    "transId MoMo không khớp với payment attempt"
            );
        }
    }

    private long providerAmount(Double amount) {
        try {
            return BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (RuntimeException exception) {
            throw providerError("MOMO_AMOUNT_MISMATCH", "Số tiền payment không phải số nguyên VND");
        }
    }

    private boolean isSuccess(int resultCode) {
        return resultCode == 0 || resultCode == 9000;
    }

    private boolean isPending(int resultCode) {
        return resultCode == 1000 || resultCode == 7000 || resultCode == 7002;
    }

    private boolean isExpired(int resultCode) {
        return resultCode == 1005;
    }

    private LocalDateTime occurredAt(ProviderPaymentResultCommand command) {
        return command.getOccurredAt() == null ? LocalDateTime.now() : command.getOccurredAt();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private PaymentProviderException providerError(String code, String message) {
        return new PaymentProviderException(code, message);
    }
}
