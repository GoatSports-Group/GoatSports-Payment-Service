package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.service.BookingCheckoutService;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingCheckoutServiceImpl implements BookingCheckoutService {
    private final PaymentService paymentService;
    private final PaymentAttemptService paymentAttemptService;

    @Override
    public BookingCheckoutResult create(CreateBookingCheckoutCommand command) {
        CreatePaymentResult created = paymentService.createPayment(CreatePaymentCommand.builder()
                .payerId(command.payerId())
                .referenceType(PaymentReferenceType.BOOKING)
                .referenceId(command.bookingId())
                .purpose(PaymentPurpose.BOOKING_REMAINING)
                .amount(command.amount())
                .currency(command.currency())
                .description("Thanh toán booking walk-in qua MoMo")
                .idempotencyKey("booking-remaining:" + command.bookingId())
                .build());

        CreatePaymentAttemptResult attempt = paymentAttemptService.createPaymentAttempt(
                CreatePaymentAttemptCommand.builder()
                        .paymentId(created.getPayment().getPaymentId())
                        .payerId(command.payerId())
                        .method(PaymentMethod.E_WALLET)
                        .locale(command.locale())
                        .ipAddress(command.ipAddress())
                        .build()
        );
        return new BookingCheckoutResult(
                created.getPayment().getPaymentId(),
                "PENDING",
                attempt.getPaymentAttempt().getCheckoutUrl(),
                created.getPayment().getExpiresAt()
        );
    }
}
