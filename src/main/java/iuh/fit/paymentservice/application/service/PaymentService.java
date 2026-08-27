package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.PaymentResponseCommand;

import java.util.UUID;

public interface PaymentService {
    CreatePaymentResult createPayment(CreatePaymentCommand command);

    PaymentResponseCommand getPayment(UUID paymentId, UUID payerUserId);
}
