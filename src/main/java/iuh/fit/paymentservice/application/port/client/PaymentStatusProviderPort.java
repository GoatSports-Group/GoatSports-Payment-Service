package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.response.payment.ProviderPaymentStatusResult;

public interface PaymentStatusProviderPort {
    ProviderPaymentStatusResult get(String providerTransactionId);

    ProviderPaymentStatusResult cancel(String providerTransactionId, String reason);
}
