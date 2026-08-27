package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentAttemptEntityMapper {
    PaymentAttempt toDomain(PaymentAttemptEntity entity);

    PaymentAttemptEntity toEntity(PaymentAttempt paymentAttempt);
}
