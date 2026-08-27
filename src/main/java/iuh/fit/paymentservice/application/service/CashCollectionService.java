package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;

public interface CashCollectionService {
    CreatePaymentAttemptResult collect(CollectCashPaymentCommand command);
}
