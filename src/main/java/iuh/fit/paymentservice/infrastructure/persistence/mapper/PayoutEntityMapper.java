package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PayoutEntityMapper {
    @Mapping(target = "settlement", ignore = true)
    @Mapping(target = "refund", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    PayoutEntity toEntity(Payout payout);

    @Mapping(target = "payoutId", ignore = true)
    @Mapping(target = "settlementId", ignore = true)
    @Mapping(target = "settlement", ignore = true)
    @Mapping(target = "refundId", ignore = true)
    @Mapping(target = "refund", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(Payout payout, @MappingTarget PayoutEntity payoutEntity);

    Payout toDomain(PayoutEntity entity);
}
