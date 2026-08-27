package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.webhook.MomoWebhookCommand;

public interface ProviderWebhookService {
    void processMomoWebhook(MomoWebhookCommand command);
}
