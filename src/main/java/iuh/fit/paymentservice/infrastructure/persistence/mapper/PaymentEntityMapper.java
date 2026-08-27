package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentEntityMapper {
    Payment toDomain(PaymentEntity entity);

    PaymentEntity toEntity(Payment payment);
}
