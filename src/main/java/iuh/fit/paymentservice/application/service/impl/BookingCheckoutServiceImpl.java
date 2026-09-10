package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingCheckoutService;
import iuh.fit.paymentservice.application.service.BookingRemainingPaymentRetryService;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCheckoutServiceImpl implements BookingCheckoutService {
    private final PaymentService paymentService;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentRepository paymentRepository;
    private final BookingRemainingPaymentRetryService bookingRemainingPaymentRetryService;

    @Override
    public BookingCheckoutResult create(CreateBookingCheckoutCommand command) {
        command.validate();
        Payment existing = paymentRepository.findByReference(
                PaymentReferenceType.BOOKING, command.bookingId(), PaymentPurpose.BOOKING_REMAINING
        ).orElse(null);
        UUID paymentId;
        LocalDateTime expiresAt;
        if (existing == null) {
            CreatePaymentResult created = paymentService.createPayment(CreatePaymentCommand.builder()
                    .payerId(command.payerId())
                    .referenceType(PaymentReferenceType.BOOKING)
                    .referenceId(command.bookingId())
                    .purpose(PaymentPurpose.BOOKING_REMAINING)
                    .amount(command.amount())
                    .currency(command.currency())
                    .description("Thanh toán số tiền còn lại của booking")
                    .idempotencyKey("booking-remaining:" + command.bookingId())
                    .expiresAt(command.expiresAt())
                    .build());
            paymentId = created.getPayment().getPaymentId();
            expiresAt = created.getPayment().getExpiresAt();
        } else {
            ensureExistingMatches(existing, command);
            Payment prepared = bookingRemainingPaymentRetryService.prepare(
                    existing.getPaymentId(), command.expiresAt()
            );
            paymentId = prepared.getPaymentId();
            expiresAt = prepared.getExpiresAt();
        }

        CreatePaymentAttemptResult attempt = paymentAttemptService.createPaymentAttempt(
                CreatePaymentAttemptCommand.builder()
                        .paymentId(paymentId)
                        .payerId(command.payerId())
                        .method(PaymentMethod.BANK_TRANSFER)
                        .locale(command.locale())
                        .ipAddress(command.ipAddress())
                        .build()
        );
        Payment refreshedPayment = paymentRepository.findById(paymentId).orElse(existing);
        String status = refreshedPayment == null
                ? PaymentStatus.PENDING.name()
                : refreshedPayment.getStatus().name();
        return new BookingCheckoutResult(
                paymentId,
                status,
                attempt.getPaymentAttempt().getCheckoutUrl(),
                attempt.getPaymentAttempt().getDeeplink(),
                attempt.getPaymentAttempt().getQrCodeContent(),
                expiresAt
        );
    }

    private void ensureExistingMatches(Payment payment, CreateBookingCheckoutCommand command) {
        if (!payment.getPayerId().equals(command.payerId())
                || Math.abs(payment.getAmount() - command.amount()) >= 0.01
                || !payment.getCurrency().equalsIgnoreCase(command.currency())) {
            throw new PaymentConflictException(
                    "BOOKING_PAYMENT_MISMATCH",
                    "Payment hiện tại không khớp booking, người thanh toán hoặc số tiền cần thu"
            );
        }
    }
}
