package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.domain.entity.Payment;

public interface PaymentCreationTransactionService {
    Payment create(Payment payment);
}
