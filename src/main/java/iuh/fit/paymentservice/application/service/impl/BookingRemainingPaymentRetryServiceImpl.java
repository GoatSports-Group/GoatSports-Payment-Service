package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingRemainingPaymentRetryService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingRemainingPaymentRetryServiceImpl implements BookingRemainingPaymentRetryService {
    private static final Set<String> NON_RETRYABLE_FAILURE_CODES = Set.of(
            "PAYOS_UNDERPAID",
            "PAYOS_AMOUNT_MISMATCH"
    );

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Override
    @Transactional
    public Payment prepare(UUID paymentId, LocalDateTime expiresAt) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureBookingRemaining(payment);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new PaymentConflictException(
                    "BOOKING_PAYMENT_ALREADY_PAID",
                    "Số tiền còn lại của booking đã được thanh toán"
            );
        }
        if (payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentConflictException(
                    "BOOKING_PAYMENT_NOT_RETRYABLE",
                    "Payment đã được hoàn tiền nên không thể tạo lại mã thanh toán"
            );
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            return payment;
        }

        boolean expiredCreatedPayment = payment.getStatus() == PaymentStatus.CREATED
                && payment.getExpiresAt() != null
                && !payment.getExpiresAt().isAfter(LocalDateTime.now());
        boolean terminalPayment = payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.EXPIRED;
        if (!expiredCreatedPayment && !terminalPayment) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.FAILED && hasNonRetryableFailure(paymentId)) {
            throw new PaymentConflictException(
                    "BOOKING_PAYMENT_REQUIRES_REVIEW",
                    "Giao dịch đã ghi nhận số tiền không khớp và cần được kiểm tra thủ công"
            );
        }

        payment.setStatus(PaymentStatus.CREATED);
        payment.setPaidAt(null);
        payment.setExpiresAt(expiresAt);
        return paymentRepository.save(payment);
    }

    private void ensureBookingRemaining(Payment payment) {
        if (payment.getReferenceType() != PaymentReferenceType.BOOKING
                || payment.getPurpose() != PaymentPurpose.BOOKING_REMAINING) {
            throw new PaymentConflictException(
                    "PAYMENT_RETRY_NOT_ALLOWED",
                    "Chỉ payment phần còn lại của booking mới được phép tạo lại mã thanh toán"
            );
        }
    }

    private boolean hasNonRetryableFailure(UUID paymentId) {
        return paymentAttemptRepository.findLatestByPaymentIds(List.of(paymentId)).stream()
                .findFirst()
                .map(PaymentAttempt::getFailureCode)
                .map(NON_RETRYABLE_FAILURE_CODES::contains)
                .orElse(false);
    }
}
