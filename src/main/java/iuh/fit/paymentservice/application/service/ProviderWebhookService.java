package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.MomoWebhookCommand;

public interface ProviderWebhookService {
    void processMomoWebhook(MomoWebhookCommand command);
}
