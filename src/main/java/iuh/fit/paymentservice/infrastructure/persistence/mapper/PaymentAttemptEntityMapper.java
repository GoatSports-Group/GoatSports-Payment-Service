package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentAttemptEntityMapper {
    PaymentAttempt toDomain(PaymentAttemptEntity entity);

    @Mapping(target = "payment", ignore = true)
    PaymentAttemptEntity toEntity(PaymentAttempt paymentAttempt);
}
