package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PaymentAttemptEntityMapper {
    PaymentAttempt toDomain(PaymentAttemptEntity entity);

    PaymentAttemptEntity toEntity(PaymentAttempt paymentAttempt);

    /** Chép lên entity đang được Hibernate quản lý; giữ nguyên quan hệ payment (chỉ đọc), khóa và audit. */
    @Mapping(target = "attemptId", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(PaymentAttempt paymentAttempt, @MappingTarget PaymentAttemptEntity entity);
}
