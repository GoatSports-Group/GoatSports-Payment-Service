package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.response.CreatePaymentAttemptResult;

public interface CashCollectionService {
    CreatePaymentAttemptResult collect(CollectCashPaymentCommand command);
}
