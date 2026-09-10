package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CollectBookingCashCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingCashCollectionService;
import iuh.fit.paymentservice.application.service.CashCollectionService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCashCollectionServiceImpl implements BookingCashCollectionService {
    private final PaymentService paymentService;
    private final CashCollectionService cashCollectionService;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentResponseCommand collect(CollectBookingCashCommand command) {
        Payment existing = paymentRepository.findByReference(
                PaymentReferenceType.BOOKING, command.bookingId(), PaymentPurpose.BOOKING_REMAINING
        ).orElse(null);
        UUID paymentId;
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
        } else {
            ensureExistingMatches(existing, command);
            paymentId = existing.getPaymentId();
        }

        cashCollectionService.collect(CollectCashPaymentCommand.builder()
                .paymentId(paymentId)
                .collectorId(command.collectorId())
                .build());

        return paymentRepository.findById(paymentId)
                .map(paymentMapper::toPaymentResponseCommand)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private void ensureExistingMatches(Payment payment, CollectBookingCashCommand command) {
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
