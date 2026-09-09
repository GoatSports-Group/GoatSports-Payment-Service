package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.refund.CreateRefundCommand;
import iuh.fit.paymentservice.application.dto.request.refund.CompleteManualRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.PreparedRefundResult;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.service.RefundTransactionService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
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
            RefundStatus.AWAITING_CLAIM,
            RefundStatus.AWAITING_BANK_ACCOUNT,
            RefundStatus.PENDING,
            RefundStatus.PROCESSING,
            RefundStatus.SUCCEEDED,
            RefundStatus.MANUAL_REVIEW
    );
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
            return new PreparedRefundResult(existing, true);
        }

        Payment payment = paymentRepository.findByIdForUpdate(command.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.getPaymentId()));

        existing = refundRepository.findByIdempotencyKey(command.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, command);
            return new PreparedRefundResult(existing, true);
        }

        validatePayment(payment);
        validateAvailableAmount(payment, command.getAmount());

        var successfulAttempt = paymentAttemptRepository
                .findLatestByPaymentIdAndStatuses(payment.getPaymentId(), SUCCEEDED_ATTEMPT_STATUS)
                .orElseThrow(() -> new PaymentConflictException(
                        "SUCCESSFUL_PAYMENT_ATTEMPT_NOT_FOUND",
                        "Không tìm thấy payment attempt thành công để hoàn tiền"
                ));

        boolean manualPayment = successfulAttempt.getProvider() == iuh.fit.paymentservice.domain.enums.PaymentProvider.MANUAL
                || successfulAttempt.getMethod() == iuh.fit.paymentservice.domain.enums.PaymentMethod.CASH;
        RefundStatus initialStatus = manualPayment
                ? RefundStatus.MANUAL_REVIEW
                : Boolean.TRUE.equals(command.getAutoDisburse())
                    ? RefundStatus.AWAITING_BANK_ACCOUNT
                    : RefundStatus.AWAITING_CLAIM;
        String failureReason = initialStatus == RefundStatus.AWAITING_BANK_ACCOUNT
                ? "Vui lòng liên kết tài khoản ngân hàng để nhận hoàn tiền"
                : null;
        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getPaymentId())
                .requestedBy(command.getRequestedBy())
                .reason(command.getReason().trim())
                .amount(command.getAmount())
                .idempotencyKey(command.getIdempotencyKey().trim())
                .autoDisburse(Boolean.TRUE.equals(command.getAutoDisburse()))
                .status(initialStatus)
                .failureReason(failureReason)
                .build());
        return new PreparedRefundResult(refund, false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund completeManualRefund(CompleteManualRefundCommand command) {
        if (command == null || command.refundId() == null || command.completedBy() == null
                || command.providerRefundId() == null || command.providerRefundId().isBlank()
                || command.providerRefundId().trim().length() > 100) {
            throw new InvalidPaymentException(
                    "MANUAL_REFUND_COMPLETION_INVALID",
                    "Refund ID, người xác nhận và mã giao dịch hoàn tiền hợp lệ là bắt buộc"
            );
        }

        Refund refund = refundRepository.findByIdForUpdate(command.refundId())
                .orElseThrow(() -> new PaymentNotFoundException(command.refundId()));
        String providerRefundId = command.providerRefundId().trim();
        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            if (Objects.equals(refund.getProviderRefundId(), providerRefundId)) return refund;
            throw new PaymentConflictException(
                    "MANUAL_REFUND_ALREADY_COMPLETED",
                    "Refund đã được xác nhận bằng một mã giao dịch khác"
            );
        }
        if (refund.getStatus() != RefundStatus.MANUAL_REVIEW) {
            throw new PaymentConflictException(
                    "MANUAL_REFUND_STATUS_INVALID",
                    "Chỉ refund đang chờ đối soát thủ công mới có thể xác nhận hoàn tất"
            );
        }

        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()));
        refund.setProviderRefundId(providerRefundId);
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setFailureReason(null);
        refund.setRefundedAt(LocalDateTime.now());
        Refund saved = refundRepository.save(refund);

        double refundedAmount = refundRepository.findByPaymentIdAndStatuses(
                        payment.getPaymentId(), EnumSet.of(RefundStatus.SUCCEEDED)
                ).stream()
                .map(Refund::getAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        PaymentStatus paymentStatus = refundedAmount + 0.001 >= payment.getAmount()
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        payment.setStatus(paymentStatus);
        paymentRepository.save(payment);
        paymentDomainEventService.recordRefundResult(saved, paymentStatus);
        return saved;
    }

    private void validateCommand(CreateRefundCommand command) {
        if (command == null
                || command.getPaymentId() == null
                || command.getRequestedBy() == null
                || command.getAmount() == null
                || command.getAutoDisburse() == null) {
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
                && Objects.equals(existing.getAutoDisburse(), command.getAutoDisburse())
                && BigDecimal.valueOf(existing.getAmount())
                .compareTo(BigDecimal.valueOf(command.getAmount())) == 0;
        if (!samePayload) {
            throw new PaymentConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key đã được dùng cho một refund khác"
            );
        }
    }

    @Override
    public Refund findById(UUID refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));
    }

}
