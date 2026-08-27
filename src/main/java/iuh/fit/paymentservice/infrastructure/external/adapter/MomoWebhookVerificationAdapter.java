package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.MomoWebhookCommand;
import iuh.fit.paymentservice.application.port.client.MomoWebhookVerificationPort;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MomoWebhookVerificationAdapter implements MomoWebhookVerificationPort {
    private final MomoProperties properties;
    private final MomoSignatureService signatureService;

    @Override
    public boolean isSignatureValid(MomoWebhookCommand command) {
        if (!properties.isEnabled() || !hasRequiredSignatureFields(command)) {
            return false;
        }
        String expected = signatureService.hmacSha256(properties.getSecretKey(), rawSignature(command));
        return signatureService.matches(expected, command.getSignature());
    }

    @Override
    public boolean isExpectedPartnerCode(String partnerCode) {
        return properties.isEnabled()
                && properties.getPartnerCode() != null
                && properties.getPartnerCode().equals(partnerCode);
    }

    @Override
    public String payloadHash(MomoWebhookCommand command) {
        return signatureService.sha256(rawPayload(command));
    }

    String rawSignature(MomoWebhookCommand command) {
        return "accessKey=" + properties.getAccessKey()
                + "&amount=" + command.getAmount()
                + "&extraData=" + normalize(command.getExtraData())
                + "&message=" + command.getMessage()
                + "&orderId=" + command.getOrderId()
                + "&orderInfo=" + command.getOrderInfo()
                + "&orderType=" + command.getOrderType()
                + "&partnerCode=" + command.getPartnerCode()
                + "&payType=" + command.getPayType()
                + "&requestId=" + command.getRequestId()
                + "&responseTime=" + command.getResponseTime()
                + "&resultCode=" + command.getResultCode()
                + "&transId=" + command.getTransId();
    }

    private String rawPayload(MomoWebhookCommand command) {
        return rawSignature(command) + "&signature=" + normalize(command.getSignature());
    }

    private boolean hasRequiredSignatureFields(MomoWebhookCommand command) {
        return command != null
                && command.getAmount() != null
                && command.getMessage() != null
                && command.getOrderId() != null
                && command.getOrderInfo() != null
                && command.getOrderType() != null
                && command.getPartnerCode() != null
                && command.getPayType() != null
                && command.getRequestId() != null
                && command.getResponseTime() != null
                && command.getResultCode() != null
                && command.getTransId() != null
                && command.getSignature() != null
                && command.getSignature().matches("[0-9a-fA-F]{64}");
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }
}
