package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;

import java.util.UUID;

public interface BookingCheckoutService {
    BookingCheckoutResult create(CreateBookingCheckoutCommand command);

    PaymentResponseCommand confirmReceived(CreateBookingCheckoutCommand command, UUID collectorId);
}
