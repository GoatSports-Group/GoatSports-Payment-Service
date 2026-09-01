package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderCheckoutResult;

public interface PaymentCheckoutPort {
    ProviderCheckoutResult createCheckout(ProviderCheckoutCommand command);
}
