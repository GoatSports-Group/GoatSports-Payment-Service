package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefundEntityMapper {
    RefundEntity toEntity(Refund refund);

    Refund toDomain(RefundEntity refundEntity);
}
