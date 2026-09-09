package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.infrastructure.persistence.entity.BankAccountEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BankAccountEntityMapper {
    BankAccount toDomain(BankAccountEntity entity);

    BankAccountEntity toEntity(BankAccount bankAccount);
}
