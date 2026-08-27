package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.ProviderCheckoutCommand;

public interface PaymentCheckoutPort {
    String createCheckoutUrl(ProviderCheckoutCommand command);
}
