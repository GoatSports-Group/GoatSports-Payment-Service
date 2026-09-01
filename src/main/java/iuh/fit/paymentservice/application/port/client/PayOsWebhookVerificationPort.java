package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.webhook.PayOsWebhookCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.VerifiedPayOsWebhook;

public interface PayOsWebhookVerificationPort {
    VerifiedPayOsWebhook verify(PayOsWebhookCommand command);

    String payloadHash(PayOsWebhookCommand command);
}
