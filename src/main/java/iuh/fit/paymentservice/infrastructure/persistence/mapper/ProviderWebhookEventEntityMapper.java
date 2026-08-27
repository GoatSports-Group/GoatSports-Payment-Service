package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.infrastructure.persistence.entity.ProviderWebhookEventEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProviderWebhookEventEntityMapper {
    ProviderWebhookEvent toDomain(ProviderWebhookEventEntity entity);

    ProviderWebhookEventEntity toEntity(ProviderWebhookEvent event);
}
