package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.WebhookProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWebhookEvent extends BaseEntity {
    private UUID webhookEventId;
    private UUID attemptId;
    private PaymentProvider provider;
    private String providerEventId;
    private String payloadHash;
    private Boolean signatureValid;
    private WebhookProcessingStatus status;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private String failureReason;
}
