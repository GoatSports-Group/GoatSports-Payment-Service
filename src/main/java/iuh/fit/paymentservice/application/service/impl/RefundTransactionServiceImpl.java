package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundStatus;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.service.RefundTransactionService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RefundTransactionServiceImpl implements RefundTransactionService {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final EnumSet<PaymentStatus> REFUNDABLE_PAYMENT_STATUSES = EnumSet.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.PARTIALLY_REFUNDED
    );
    private static final EnumSet<RefundStatus> RESERVED_REFUND_STATUSES = EnumSet.of(
            RefundStatus.CREATED,
            RefundStatus.PENDING,
            RefundStatus.SUCCEEDED,
            RefundStatus.MANUAL_REVIEW
    );
    private static final EnumSet<RefundStatus> SUCCEEDED_REFUND_STATUS =
            EnumSet.of(RefundStatus.SUCCEEDED);
    private static final EnumSet<PaymentAttemptStatus> SUCCEEDED_ATTEMPT_STATUS =
            EnumSet.of(PaymentAttemptStatus.SUCCEEDED);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final PaymentDomainEventService paymentDomainEventService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedRefundResult prepare(CreateRefundCommand command) {
        validateCommand(command);
        Refund existing = refundRepository.findByIdempotencyKey(command.getIdempotencyKey())
                .orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, command);
            return new PreparedRefundResult(existing, null, null, true);
        }

        Payment payment = paymentRepository.findByIdForUpdate(command.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.getPaymentId()));

        existing = refundRepository.findByIdempotencyKey(command.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, command);
            return new PreparedRefundResult(existing, null, null, true);
        }

        validatePayment(payment);
        validateAvailableAmount(payment, command.getAmount());

        PaymentAttempt successfulAttempt = paymentAttemptRepository
                .findLatestByPaymentIdAndStatuses(payment.getPaymentId(), SUCCEEDED_ATTEMPT_STATUS)
                .orElseThrow(() -> new PaymentConflictException(
                        "SUCCESSFUL_PAYMENT_ATTEMPT_NOT_FOUND",
                        "Không tìm thấy payment attempt thành công để hoàn tiền"
                ));

        RefundStatus initialStatus = successfulAttempt.getProvider() == PaymentProvider.MANUAL
                ? RefundStatus.MANUAL_REVIEW
                : RefundStatus.PENDING;
        String failureReason = initialStatus == RefundStatus.MANUAL_REVIEW
                ? "Payment được thu thủ công; cần hoàn tiền ngoài hệ thống và xác nhận thủ công"
                : null;
        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getPaymentId())
                .requestedBy(command.getRequestedBy())
                .reason(command.getReason().trim())
                .amount(command.getAmount())
                .idempotencyKey(command.getIdempotencyKey().trim())
                .status(initialStatus)
                .failureReason(failureReason)
                .build());
        if (refund.getStatus() == RefundStatus.MANUAL_REVIEW) {
            paymentDomainEventService.recordRefundResult(refund, payment.getStatus());
        }

        return new PreparedRefundResult(
                refund,
                successfulAttempt.getProvider(),
                successfulAttempt.getProviderTransactionId(),
                false
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund complete(UUID refundId, ProviderRefundResult providerResult) {
        if (providerResult == null || providerResult.getStatus() == null) {
            throw new InvalidPaymentException("REFUND_PROVIDER_RESULT_INVALID", "Kết quả refund provider không hợp lệ");
        }
        Refund refund = findRefundForUpdate(refundId);
        if (refund.getStatus() == RefundStatus.SUCCEEDED || refund.getStatus() == RefundStatus.FAILED) {
            return refund;
        }

        if (providerResult.getProviderRefundId() != null
                && !providerResult.getProviderRefundId().isBlank()) {
            refund.setProviderRefundId(truncate(providerResult.getProviderRefundId(), 100));
        }

        if (providerResult.getStatus() == ProviderRefundStatus.SUCCEEDED) {
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setFailureReason(null);
            refund.setRefundedAt(LocalDateTime.now());
            refund = refundRepository.save(refund);
            Payment payment = updatePaymentRefundStatus(refund);
            paymentDomainEventService.recordRefundResult(refund, payment.getStatus());
            return refund;
        }
        if (providerResult.getStatus() == ProviderRefundStatus.PENDING) {
            refund.setStatus(RefundStatus.PENDING);
            refund.setFailureReason(truncate(providerResult.getFailureReason(), 500));
            return refundRepository.save(refund);
        }
        if (providerResult.getStatus() == ProviderRefundStatus.MANUAL_REVIEW) {
            if (refund.getStatus() == RefundStatus.MANUAL_REVIEW) {
                return refund;
            }
            refund.setStatus(RefundStatus.MANUAL_REVIEW);
            refund.setFailureReason(truncate(providerResult.getFailureReason(), 500));
            refund = refundRepository.save(refund);
            paymentDomainEventService.recordRefundResult(refund, currentPaymentStatus(refund));
            return refund;
        }

        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(truncate(providerResult.getFailureReason(), 500));
        refund = refundRepository.save(refund);
        paymentDomainEventService.recordRefundResult(refund, currentPaymentStatus(refund));
        return refund;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund markManualReview(UUID refundId, String reason) {
        Refund refund = findRefundForUpdate(refundId);
        if (refund.getStatus() == RefundStatus.SUCCEEDED || refund.getStatus() == RefundStatus.FAILED) {
            return refund;
        }
        if (refund.getStatus() == RefundStatus.MANUAL_REVIEW) {
            return refund;
        }
        refund.setStatus(RefundStatus.MANUAL_REVIEW);
        refund.setFailureReason(truncate(reason, 500));
        refund = refundRepository.save(refund);
        paymentDomainEventService.recordRefundResult(refund, currentPaymentStatus(refund));
        return refund;
    }

    private Payment updatePaymentRefundStatus(Refund currentRefund) {
        Payment payment = paymentRepository.findByIdForUpdate(currentRefund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(currentRefund.getPaymentId()));
        BigDecimal successfulAmount = refundRepository.findByPaymentIdAndStatuses(
                        payment.getPaymentId(),
                        SUCCEEDED_REFUND_STATUS
                )
                .stream()
                .map(Refund::getAmount)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paymentAmount = BigDecimal.valueOf(payment.getAmount());
        payment.setStatus(successfulAmount.compareTo(paymentAmount) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
        return paymentRepository.save(payment);
    }

    private PaymentStatus currentPaymentStatus(Refund refund) {
        return paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()))
                .getStatus();
    }

    private void validateCommand(CreateRefundCommand command) {
        if (command == null
                || command.getPaymentId() == null
                || command.getRequestedBy() == null
                || command.getAmount() == null) {
            throw new InvalidPaymentException(
                    "REFUND_REQUEST_INVALID",
                    "Payment ID, requestedBy và amount là bắt buộc"
            );
        }
        if (!Double.isFinite(command.getAmount()) || command.getAmount() <= 0) {
            throw new InvalidPaymentException("REFUND_AMOUNT_INVALID", "Số tiền refund phải lớn hơn 0");
        }
        try {
            BigDecimal.valueOf(command.getAmount()).setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidPaymentException("REFUND_AMOUNT_INVALID", "Số tiền refund phải là số nguyên VND");
        }
        if (command.getReason() == null
                || command.getReason().isBlank()
                || command.getReason().trim().length() > 500) {
            throw new InvalidPaymentException("REFUND_REASON_INVALID", "Lý do refund phải có từ 1 đến 500 ký tự");
        }
        if (command.getIdempotencyKey() == null
                || !IDEMPOTENCY_KEY_PATTERN.matcher(command.getIdempotencyKey().trim()).matches()) {
            throw new InvalidPaymentException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key phải dài 8-128 ký tự và chỉ chứa chữ, số, '.', '_', ':', '-'"
            );
        }
    }

    private void validatePayment(Payment payment) {
        if (!REFUNDABLE_PAYMENT_STATUSES.contains(payment.getStatus())) {
            throw new PaymentConflictException(
                    "PAYMENT_NOT_REFUNDABLE",
                    "Trạng thái payment hiện tại không cho phép refund"
            );
        }
        if (!"VND".equals(payment.getCurrency())) {
            throw new InvalidPaymentException("UNSUPPORTED_CURRENCY", "Refund hiện chỉ hỗ trợ VND");
        }
    }

    private void validateAvailableAmount(Payment payment, Double requestedAmount) {
        BigDecimal reserved = refundRepository.findByPaymentIdAndStatuses(
                        payment.getPaymentId(),
                        RESERVED_REFUND_STATUSES
                )
                .stream()
                .map(Refund::getAmount)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = BigDecimal.valueOf(payment.getAmount()).subtract(reserved);
        if (BigDecimal.valueOf(requestedAmount).compareTo(available) > 0) {
            throw new PaymentConflictException(
                    "REFUND_AMOUNT_EXCEEDS_AVAILABLE",
                    "Tổng số tiền refund không được vượt số tiền payment còn lại"
            );
        }
    }

    private void validateIdempotentReplay(Refund existing, CreateRefundCommand command) {
        boolean samePayload = existing.getPaymentId().equals(command.getPaymentId())
                && existing.getRequestedBy().equals(command.getRequestedBy())
                && Objects.equals(existing.getReason(), command.getReason().trim())
                && BigDecimal.valueOf(existing.getAmount())
                .compareTo(BigDecimal.valueOf(command.getAmount())) == 0;
        if (!samePayload) {
            throw new PaymentConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key đã được dùng cho một refund khác"
            );
        }
    }

    private Refund findRefundForUpdate(UUID refundId) {
        return refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new PaymentConflictException(
                        "REFUND_NOT_FOUND",
                        "Không tìm thấy refund"
                ));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}