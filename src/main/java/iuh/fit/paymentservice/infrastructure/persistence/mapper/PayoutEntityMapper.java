package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PayoutEntityMapper {
    @Mapping(target = "settlement", ignore = true)
    @Mapping(target = "refund", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    PayoutEntity toEntity(Payout payout);

    Payout toDomain(PayoutEntity entity);
}
