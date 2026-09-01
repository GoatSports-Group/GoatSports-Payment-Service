package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
public interface BookingCheckoutService {
    BookingCheckoutResult create(CreateBookingCheckoutCommand command);
}
