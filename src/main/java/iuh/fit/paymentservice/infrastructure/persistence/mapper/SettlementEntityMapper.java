package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Settlement;
import iuh.fit.paymentservice.infrastructure.persistence.entity.SettlementEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SettlementEntityMapper {
    SettlementEntity toEntity(Settlement settlement);

    @Mapping(target = "settlementId", ignore = true)
    @Mapping(target = "payeeId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(Settlement settlement, @MappingTarget SettlementEntity settlementEntity);

    Settlement toDomain(SettlementEntity settlementEntity);
}
