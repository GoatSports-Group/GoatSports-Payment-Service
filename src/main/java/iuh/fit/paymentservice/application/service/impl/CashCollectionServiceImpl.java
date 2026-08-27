package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.mapper.PaymentAttemptMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.CashCollectionService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashCollectionServiceImpl implements CashCollectionService {
    private static final EnumSet<PaymentAttemptStatus> ACTIVE_ATTEMPT_STATUSES = EnumSet.of(
            PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.REDIRECT_READY,
            PaymentAttemptStatus.PENDING
    );
    private static final EnumSet<PaymentAttemptStatus> SUCCEEDED_STATUS = EnumSet.of(
            PaymentAttemptStatus.SUCCEEDED
    );
    private static final EnumSet<PaymentStatus> COLLECTIBLE_PAYMENT_STATUSES = EnumSet.of(
            PaymentStatus.CREATED,
            PaymentStatus.PENDING,
            PaymentStatus.FAILED
    );

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptMapper paymentAttemptMapper;
    private final PaymentDomainEventService paymentDomainEventService;

    @Override
    @Transactional
    public CreatePaymentAttemptResult collect(CollectCashPaymentCommand command) {
        validateCommand(command);
        LocalDateTime now = LocalDateTime.now();
        Payment payment = paymentRepository.findByIdForUpdate(command.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.getPaymentId()));

        Optional<PaymentAttempt> existingCashCollection =
                paymentAttemptRepository.findLatestByPaymentIdAndProviderAndMethodAndStatuses(
                        payment.getPaymentId(),
                        PaymentProvider.MANUAL,
                        PaymentMethod.CASH,
                        SUCCEEDED_STATUS
                );
        if (existingCashCollection.isPresent()) {
            return result(existingCashCollection.get(), true);
        }

        validatePayment(payment, now);
        paymentAttemptRepository.findLatestByPaymentIdAndStatuses(
                payment.getPaymentId(),
                ACTIVE_ATTEMPT_STATUSES
        ).ifPresent(attempt -> {
            throw new PaymentConflictException(
                    "PAYMENT_ATTEMPT_ACTIVE",
                    "Payment đang có một attempt hoạt động; không thể xác nhận thu tiền mặt"
            );
        });

        PaymentAttempt cashAttempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                .paymentId(payment.getPaymentId())
                .provider(PaymentProvider.MANUAL)
                .method(PaymentMethod.CASH)
                .status(PaymentAttemptStatus.CREATED)
                .initiatedAt(now)
                .build());
        cashAttempt.setProviderTransactionId(manualReference(cashAttempt));
        cashAttempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        cashAttempt.setCompletedAt(now);
        cashAttempt = paymentAttemptRepository.save(cashAttempt);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(now);
        paymentRepository.save(payment);
        paymentDomainEventService.recordPaymentResult(payment, cashAttempt, command.getCollectorId());
        return result(cashAttempt, false);
    }

    private void validateCommand(CollectCashPaymentCommand command) {
        if (command == null || command.getPaymentId() == null || command.getCollectorId() == null) {
            throw new InvalidPaymentException(
                    "CASH_COLLECTION_REQUEST_INVALID",
                    "Payment ID và collector ID là bắt buộc"
            );
        }
    }

    private void validatePayment(Payment payment, LocalDateTime now) {
        if (!COLLECTIBLE_PAYMENT_STATUSES.contains(payment.getStatus())) {
            throw new PaymentConflictException(
                    "PAYMENT_NOT_COLLECTIBLE",
                    "Trạng thái payment hiện tại không cho phép thu tiền mặt"
            );
        }
        if (payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(now)) {
            throw new PaymentConflictException("PAYMENT_EXPIRED", "Payment đã hết hạn");
        }
        if (!"VND".equals(payment.getCurrency())) {
            throw new InvalidPaymentException(
                    "UNSUPPORTED_CURRENCY",
                    "Thu tiền mặt hiện chỉ hỗ trợ VND"
            );
        }
    }

    private String manualReference(PaymentAttempt attempt) {
        return "CASH-" + attempt.getAttemptId().toString().replace("-", "");
    }

    private CreatePaymentAttemptResult result(PaymentAttempt attempt, boolean replayed) {
        return new CreatePaymentAttemptResult(
                paymentAttemptMapper.toPaymentAttemptResponseCommand(attempt),
                replayed
        );
    }
}
