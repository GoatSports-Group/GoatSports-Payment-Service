package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingCheckoutService;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class BookingCheckoutServiceImpl implements BookingCheckoutService {
    private final PaymentService paymentService;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentDomainEventService paymentDomainEventService;

    @Override
    public BookingCheckoutResult create(CreateBookingCheckoutCommand command) {
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
                    .build());
            paymentId = created.getPayment().getPaymentId();
            expiresAt = created.getPayment().getExpiresAt();
        } else {
            validateExisting(existing, command);
            paymentId = existing.getPaymentId();
            expiresAt = existing.getExpiresAt();
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

    @Override
    @Transactional
    public PaymentResponseCommand confirmReceived(
            CreateBookingCheckoutCommand command,
            UUID collectorId
    ) {
        if (collectorId == null) {
            throw new PaymentConflictException(
                    "VIETQR_COLLECTOR_REQUIRED",
                    "Thiếu người xác nhận giao dịch VietQR"
            );
        }
        Payment existing = paymentRepository.findByReference(
                PaymentReferenceType.BOOKING,
                command.bookingId(),
                PaymentPurpose.BOOKING_REMAINING
        ).orElseThrow(() -> new PaymentConflictException(
                "VIETQR_PAYMENT_NOT_FOUND",
                "Booking chưa có giao dịch VietQR cần xác nhận"
        ));
        validateExisting(existing, command);

        Payment payment = paymentRepository.findByIdForUpdate(existing.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(existing.getPaymentId()));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return paymentMapper.toPaymentResponseCommand(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentConflictException(
                    "VIETQR_PAYMENT_NOT_PENDING",
                    "Giao dịch VietQR không còn ở trạng thái chờ xác nhận"
            );
        }

        PaymentAttempt attempt = paymentAttemptRepository.findLatestByPaymentIdAndStatuses(
                payment.getPaymentId(),
                EnumSet.of(
                        PaymentAttemptStatus.CREATED,
                        PaymentAttemptStatus.REDIRECT_READY,
                        PaymentAttemptStatus.PENDING
                )
        ).filter(item -> item.getProvider() == PaymentProvider.VIETQR
                && item.getMethod() == PaymentMethod.BANK_TRANSFER)
                .orElseThrow(() -> new PaymentConflictException(
                        "VIETQR_ATTEMPT_NOT_FOUND",
                        "Không tìm thấy mã VietQR đang chờ xác nhận"
                ));

        LocalDateTime now = LocalDateTime.now();
        attempt.setProviderTransactionId(
                "VIETQR-MANUAL-" + attempt.getAttemptId().toString().replace("-", "")
        );
        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        attempt.setCompletedAt(now);
        attempt = paymentAttemptRepository.save(attempt);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(now);
        payment = paymentRepository.save(payment);
        paymentDomainEventService.recordPaymentResult(payment, attempt, collectorId);
        return paymentMapper.toPaymentResponseCommand(payment);
    }

    private void validateExisting(Payment payment, CreateBookingCheckoutCommand command) {
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
