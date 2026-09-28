package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.ProviderWebhookEventRepository;
import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.infrastructure.persistence.entity.ProviderWebhookEventEntity;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.ProviderWebhookEventEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaProviderWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProviderWebhookEventRepositoryImpl implements ProviderWebhookEventRepository {
    private final JpaProviderWebhookEventRepository jpaRepository;
    private final ProviderWebhookEventEntityMapper mapper;

    @Override
    public ProviderWebhookEvent save(ProviderWebhookEvent event) {
        ProviderWebhookEventEntity entity = event.getWebhookEventId() == null
                ? mapper.toEntity(event)
                : jpaRepository.findById(event.getWebhookEventId())
                        .map(existing -> {
                            mapper.updateEntity(event, existing);
                            return existing;
                        })
                        .orElseGet(() -> mapper.toEntity(event));
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<ProviderWebhookEvent> findByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    ) {
        return jpaRepository.findByProviderAndProviderEventId(provider, providerEventId)
                .map(mapper::toDomain);
    }
}
