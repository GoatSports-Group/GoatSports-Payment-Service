package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.infrastructure.persistence.entity.ProviderWebhookEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProviderWebhookEventEntityMapper {
    ProviderWebhookEvent toDomain(ProviderWebhookEventEntity entity);

    @Mapping(target = "attempt", ignore = true)
    ProviderWebhookEventEntity toEntity(ProviderWebhookEvent event);

    /** Chép lên entity đang được Hibernate quản lý; giữ nguyên quan hệ attempt (chỉ đọc), khóa và audit. */
    @Mapping(target = "webhookEventId", ignore = true)
    @Mapping(target = "attemptId", ignore = true)
    @Mapping(target = "attempt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(ProviderWebhookEvent event, @MappingTarget ProviderWebhookEventEntity entity);
}
