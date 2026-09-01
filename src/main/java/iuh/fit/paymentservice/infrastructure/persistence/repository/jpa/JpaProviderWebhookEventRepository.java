package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.infrastructure.persistence.entity.ProviderWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaProviderWebhookEventRepository extends JpaRepository<ProviderWebhookEventEntity, UUID> {
    Optional<ProviderWebhookEventEntity> findByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    );
}
