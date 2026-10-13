package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface RefundEntityMapper {
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "retryCount", defaultValue = "0")
    @Mapping(target = "lastRemindedAt", ignore = true)
    RefundEntity toEntity(Refund refund);

    @Mapping(target = "refundId", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "retryCount", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "lastRemindedAt", ignore = true)
    void updateEntity(Refund refund, @MappingTarget RefundEntity refundEntity);

    Refund toDomain(RefundEntity refundEntity);
}
