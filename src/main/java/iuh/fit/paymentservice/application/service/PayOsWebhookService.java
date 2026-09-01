package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.webhook.PayOsWebhookCommand;

public interface PayOsWebhookService {
    void process(PayOsWebhookCommand command);
}
