package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.infrastructure.persistence.entity.ProviderWebhookEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProviderWebhookEventEntityMapper {
    ProviderWebhookEvent toDomain(ProviderWebhookEventEntity entity);

    @Mapping(target = "attempt", ignore = true)
    ProviderWebhookEventEntity toEntity(ProviderWebhookEvent event);
}
