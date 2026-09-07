package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RefundEntityMapper {
    @Mapping(target = "payment", ignore = true)
    RefundEntity toEntity(Refund refund);

    Refund toDomain(RefundEntity refundEntity);
}
