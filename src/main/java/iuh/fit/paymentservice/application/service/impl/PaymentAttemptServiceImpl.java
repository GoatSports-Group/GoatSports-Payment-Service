package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.request.payment.ProviderPaymentResultCommand;
import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.dto.request.webhook.ProviderTransactionQueryCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.attempt.ProviderCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderTransactionStatus;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentAccessDeniedException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.mapper.PaymentAttemptMapper;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.application.port.client.ProviderTransactionQueryPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.ProviderPaymentResultService;
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
public class PaymentAttemptServiceImpl implements PaymentAttemptService {
    private static final EnumSet<PaymentAttemptStatus> ACTIVE_ATTEMPT_STATUSES = EnumSet.of(
            PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.REDIRECT_READY,
            PaymentAttemptStatus.PENDING
    );
    private static final String LEGACY_DIRECT_QR_FAILURE = "MOMO_DIRECT_QR_UNAVAILABLE";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentCheckoutPort paymentCheckoutPort;
    private final ProviderTransactionQueryPort transactionQueryPort;
    private final ProviderPaymentResultService providerPaymentResultService;
    private final PaymentAttemptMapper paymentAttemptMapper;

    @Override
    @Transactional(noRollbackFor = PaymentProviderException.class)
    public CreatePaymentAttemptResult createPaymentAttempt(CreatePaymentAttemptCommand command) {
        validateCommand(command);
        LocalDateTime now = LocalDateTime.now();
        Payment payment = paymentRepository.findByIdForUpdate(command.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.getPaymentId()));

        validatePayment(payment, command, now);

        Optional<PaymentAttempt> activeAttempt = paymentAttemptRepository.findLatestByPaymentIdAndStatuses(
                payment.getPaymentId(),
                ACTIVE_ATTEMPT_STATUSES
        );
        if (activeAttempt.isPresent()) {
            PaymentAttempt existing = activeAttempt.get();
            if (existing.getMethod() != command.getMethod()) {
                throw new PaymentConflictException(
                        "PAYMENT_ATTEMPT_ACTIVE",
                        "Payment đang có một attempt hoạt động với phương thức khác"
                );
            }
            PaymentAttempt reconciled = reconcileActiveAttempt(existing, payment, command);
            if (ACTIVE_ATTEMPT_STATUSES.contains(reconciled.getStatus())
                    || reconciled.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
                return new CreatePaymentAttemptResult(
                        paymentAttemptMapper.toPaymentAttemptResponseCommand(reconciled),
                        true
                );
            }
        } else {
            Optional<PaymentAttempt> recoverableAttempt = paymentAttemptRepository
                    .findLatestByPaymentIdAndStatuses(
                            payment.getPaymentId(),
                            EnumSet.of(PaymentAttemptStatus.FAILED)
                    )
                    .filter(attempt -> LEGACY_DIRECT_QR_FAILURE.equals(attempt.getFailureCode()));
            if (recoverableAttempt.isPresent()) {
                PaymentAttempt recovered = prepareCheckout(
                        recoverableAttempt.get(), payment, command, now
                );
                return new CreatePaymentAttemptResult(
                        paymentAttemptMapper.toPaymentAttemptResponseCommand(recovered),
                        true
                );
            }
        }

        PaymentAttempt paymentAttempt = PaymentAttempt.builder()
                .paymentId(payment.getPaymentId())
                .provider(PaymentProvider.MOMO)
                .method(command.getMethod())
                .status(PaymentAttemptStatus.CREATED)
                .initiatedAt(now)
                .build();
        paymentAttempt = paymentAttemptRepository.save(paymentAttempt);

        paymentAttempt = prepareCheckout(paymentAttempt, payment, command, now);

        return new CreatePaymentAttemptResult(
                paymentAttemptMapper.toPaymentAttemptResponseCommand(paymentAttempt),
                false
        );
    }

    private PaymentAttempt prepareCheckout(
            PaymentAttempt paymentAttempt,
            Payment payment,
            CreatePaymentAttemptCommand command,
            LocalDateTime now
    ) {
        ProviderCheckoutResult checkout;
        try {
            checkout = paymentCheckoutPort.createCheckout(
                    ProviderCheckoutCommand.builder()
                            .attemptId(paymentAttempt.getAttemptId())
                            .amount(payment.getAmount())
                            .currency(payment.getCurrency())
                            .orderInfo("GoatSports payment "
                                    + payment.getPaymentId().toString().replace("-", ""))
                            .ipAddress(command.getIpAddress())
                            .locale(command.getLocale())
                            .method(command.getMethod())
                            .initiatedAt(now)
                            .paymentExpiresAt(payment.getExpiresAt())
                            .build()
            );

        } catch (PaymentProviderException exception) {
            markFailed(paymentAttempt, exception.getCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            PaymentProviderException providerException = new PaymentProviderException(
                    "MOMO_CHECKOUT_ERROR",
                    "Không thể tạo URL thanh toán MoMo",
                    exception
            );
            markFailed(paymentAttempt, providerException.getCode(), providerException.getMessage());
            throw providerException;
        }

        paymentAttempt.setCheckoutUrl(checkout.checkoutUrl());
        paymentAttempt.setDeeplink(checkout.deeplink());
        paymentAttempt.setQrCodeContent(checkout.qrCodeContent());
        paymentAttempt.setStatus(PaymentAttemptStatus.REDIRECT_READY);
        paymentAttempt.setFailureCode(null);
        paymentAttempt.setFailureReason(null);
        paymentAttempt.setCompletedAt(null);
        paymentAttempt = paymentAttemptRepository.save(paymentAttempt);

        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        return paymentAttempt;
    }

    private PaymentAttempt reconcileActiveAttempt(
            PaymentAttempt attempt,
            Payment payment,
            CreatePaymentAttemptCommand command
    ) {
        ProviderTransactionStatus status = transactionQueryPort.query(
                ProviderTransactionQueryCommand.builder()
                        .attemptId(attempt.getAttemptId())
                        .expectedAmount(payment.getAmount())
                        .locale(command.getLocale())
                        .build()
        );
        providerPaymentResultService.apply(ProviderPaymentResultCommand.builder()
                .attemptId(attempt.getAttemptId())
                .providerTransactionId(status.getProviderTransactionId())
                .amount(status.getAmount())
                .resultCode(status.getResultCode())
                .message(status.getMessage())
                .occurredAt(status.getOccurredAt())
                .build());
        return paymentAttemptRepository.findById(attempt.getAttemptId()).orElse(attempt);
    }

    private void validateCommand(CreatePaymentAttemptCommand command) {
        if (command.getPaymentId() == null || command.getPayerId() == null) {
            throw new InvalidPaymentException(
                    "PAYMENT_ATTEMPT_REQUEST_INVALID",
                    "Payment ID và payer ID là bắt buộc"
            );
        }
        if (command.getMethod() == null) {
            throw new InvalidPaymentException("PAYMENT_METHOD_REQUIRED", "Phương thức thanh toán là bắt buộc");
        }
        if (command.getMethod() != PaymentMethod.E_WALLET) {
            throw new InvalidPaymentException(
                    "UNSUPPORTED_PAYMENT_METHOD",
                    "Checkout MoMo chỉ hỗ trợ phương thức E_WALLET"
            );
        }
        if (command.getLocale() != null
                && !command.getLocale().isBlank()
                && !command.getLocale().equalsIgnoreCase("vn")
                && !command.getLocale().equalsIgnoreCase("en")) {
            throw new InvalidPaymentException("MOMO_INVALID_LOCALE", "MoMo chỉ hỗ trợ locale vn hoặc en");
        }
        if (command.getIpAddress() == null
                || command.getIpAddress().isBlank()
                || command.getIpAddress().length() > 45) {
            throw new InvalidPaymentException("MOMO_INVALID_IP_ADDRESS", "Địa chỉ IP thanh toán không hợp lệ");
        }
    }

    private void validatePayment(
            Payment payment,
            CreatePaymentAttemptCommand command,
            LocalDateTime now
    ) {
        if (!payment.getPayerId().equals(command.getPayerId())) {
            throw new PaymentAccessDeniedException();
        }
        if (payment.getStatus() != PaymentStatus.CREATED
                && payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.FAILED) {
            throw new PaymentConflictException(
                    "PAYMENT_NOT_PAYABLE",
                    "Trạng thái payment hiện tại không cho phép tạo checkout"
            );
        }
        if (payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(now)) {
            throw new PaymentConflictException("PAYMENT_EXPIRED", "Payment đã hết hạn");
        }
        if (!"VND".equals(payment.getCurrency())) {
            throw new InvalidPaymentException("UNSUPPORTED_CURRENCY", "MoMo hiện chỉ hỗ trợ VND");
        }
    }

    private void markFailed(PaymentAttempt paymentAttempt, String code, String reason) {
        paymentAttempt.setStatus(PaymentAttemptStatus.FAILED);
        paymentAttempt.setFailureCode(truncate(code, 64));
        paymentAttempt.setFailureReason(truncate(reason, 500));
        paymentAttempt.setCompletedAt(LocalDateTime.now());
        paymentAttemptRepository.save(paymentAttempt);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
