package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CollectBookingCashCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingCashCollectionService;
import iuh.fit.paymentservice.application.service.CashCollectionService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingCashCollectionServiceImpl implements BookingCashCollectionService {
    private final PaymentService paymentService;
    private final CashCollectionService cashCollectionService;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentResponseCommand collect(CollectBookingCashCommand command) {
        CreatePaymentResult created = paymentService.createPayment(CreatePaymentCommand.builder()
                .payerId(command.payerId())
                .referenceType(PaymentReferenceType.BOOKING)
                .referenceId(command.bookingId())
                .purpose(PaymentPurpose.BOOKING_REMAINING)
                .amount(command.amount())
                .currency(command.currency())
                .description("Thu tiền còn lại khi check-in")
                .idempotencyKey("booking-remaining:" + command.bookingId())
                .build());

        cashCollectionService.collect(CollectCashPaymentCommand.builder()
                .paymentId(created.getPayment().getPaymentId())
                .collectorId(command.collectorId())
                .build());

        return paymentRepository.findById(created.getPayment().getPaymentId())
                .map(paymentMapper::toPaymentResponseCommand)
                .orElseThrow(() -> new PaymentNotFoundException(created.getPayment().getPaymentId()));
    }
}
