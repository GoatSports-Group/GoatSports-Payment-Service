package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.response.CreatePaymentAttemptResult;

public interface PaymentAttemptService {
    CreatePaymentAttemptResult createPaymentAttempt(CreatePaymentAttemptCommand command);
}
