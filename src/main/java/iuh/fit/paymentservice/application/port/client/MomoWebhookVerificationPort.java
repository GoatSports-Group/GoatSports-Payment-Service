package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.MomoWebhookCommand;

public interface MomoWebhookVerificationPort {
    boolean isSignatureValid(MomoWebhookCommand command);

    boolean isExpectedPartnerCode(String partnerCode);

    String payloadHash(MomoWebhookCommand command);
}
