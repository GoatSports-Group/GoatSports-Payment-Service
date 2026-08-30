package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.CollectBookingCashCommand;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;

public interface BookingCashCollectionService {
    PaymentResponseCommand collect(CollectBookingCashCommand command);
}
