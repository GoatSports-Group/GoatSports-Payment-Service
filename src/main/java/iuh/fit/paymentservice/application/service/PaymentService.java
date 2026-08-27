package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;

import java.util.UUID;

public interface PaymentService {
    CreatePaymentResult createPayment(CreatePaymentCommand command);

    PaymentResponseCommand getPayment(UUID paymentId, UUID payerUserId);
}
