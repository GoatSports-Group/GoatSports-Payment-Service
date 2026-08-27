package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.ProviderPaymentResultCommand;

public interface ProviderPaymentResultService {
    boolean apply(ProviderPaymentResultCommand command);
}
