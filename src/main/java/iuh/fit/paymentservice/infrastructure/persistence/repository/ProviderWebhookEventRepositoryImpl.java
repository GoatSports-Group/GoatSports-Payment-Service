package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.ProviderWebhookEventRepository;
import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
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
        return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toEntity(event)));
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
