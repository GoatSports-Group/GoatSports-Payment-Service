package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;

import java.util.Optional;

public interface ProviderWebhookEventRepository {
    ProviderWebhookEvent save(ProviderWebhookEvent event);

    Optional<ProviderWebhookEvent> findByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    );
}
