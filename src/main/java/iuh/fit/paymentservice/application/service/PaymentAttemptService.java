package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;

public interface PaymentAttemptService {
    CreatePaymentAttemptResult createPaymentAttempt(CreatePaymentAttemptCommand command);
}
