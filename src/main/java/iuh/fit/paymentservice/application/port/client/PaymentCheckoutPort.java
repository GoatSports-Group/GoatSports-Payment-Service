package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;

public interface PaymentCheckoutPort {
    String createCheckoutUrl(ProviderCheckoutCommand command);
}
