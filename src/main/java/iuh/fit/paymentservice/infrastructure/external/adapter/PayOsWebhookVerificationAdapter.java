package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.PayOsWebhookCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.VerifiedPayOsWebhook;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.client.PayOsWebhookVerificationPort;
import iuh.fit.paymentservice.infrastructure.external.config.PayOsClientProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class PayOsWebhookVerificationAdapter implements PayOsWebhookVerificationPort {
    private final PayOsClientProvider clientProvider;

    @Override
    public VerifiedPayOsWebhook verify(PayOsWebhookCommand command) {
        if (command == null || command.data() == null || isBlank(command.signature())) {
            throw new InvalidPaymentException("PAYOS_WEBHOOK_INVALID", "Webhook payOS thiếu dữ liệu hoặc signature");
        }
        PayOsWebhookCommand.Data source = command.data();
        WebhookData data = WebhookData.builder()
                .orderCode(source.orderCode())
                .amount(source.amount())
                .description(source.description())
                .accountNumber(source.accountNumber())
                .reference(source.reference())
                .transactionDateTime(source.transactionDateTime())
                .currency(source.currency())
                .paymentLinkId(source.paymentLinkId())
                .code(source.code())
                .desc(source.desc())
                .counterAccountBankId(source.counterAccountBankId())
                .counterAccountBankName(source.counterAccountBankName())
                .counterAccountName(source.counterAccountName())
                .counterAccountNumber(source.counterAccountNumber())
                .virtualAccountName(source.virtualAccountName())
                .virtualAccountNumber(source.virtualAccountNumber())
                .build();
        Webhook webhook = Webhook.builder()
                .code(command.code())
                .desc(command.desc())
                .success(command.success())
                .data(data)
                .signature(command.signature())
                .build();
        try {
            WebhookData verified = clientProvider.get().webhooks().verify(webhook);
            return new VerifiedPayOsWebhook(
                    verified.getOrderCode(),
                    verified.getAmount(),
                    verified.getDescription(),
                    verified.getReference(),
                    verified.getTransactionDateTime(),
                    verified.getCurrency(),
                    verified.getPaymentLinkId(),
                    verified.getCode(),
                    verified.getDesc()
            );
        } catch (RuntimeException exception) {
            throw new InvalidPaymentException(
                    "PAYOS_WEBHOOK_SIGNATURE_INVALID",
                    "Signature webhook payOS không hợp lệ"
            );
        }
    }

    @Override
    public String payloadHash(PayOsWebhookCommand command) {
        PayOsWebhookCommand.Data data = command == null ? null : command.data();
        String normalized = command == null ? "null" : String.join("|",
                safe(command.code()), safe(command.desc()), String.valueOf(command.success()),
                data == null ? "" : String.valueOf(data.orderCode()),
                data == null ? "" : String.valueOf(data.amount()),
                data == null ? "" : safe(data.description()),
                data == null ? "" : safe(data.reference()),
                data == null ? "" : safe(data.transactionDateTime()),
                data == null ? "" : safe(data.currency()),
                data == null ? "" : safe(data.paymentLinkId()),
                data == null ? "" : safe(data.code()),
                safe(command.signature())
        );
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
