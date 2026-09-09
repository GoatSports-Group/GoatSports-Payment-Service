package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.BankAccountEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaBankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BankAccountRepositoryImpl implements BankAccountRepository {
    private final JpaBankAccountRepository jpaRepository;
    private final BankAccountEntityMapper mapper;

    @Override
    public BankAccount save(BankAccount bankAccount) {
        return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toEntity(bankAccount)));
    }

    @Override
    public Optional<BankAccount> findByIdForUpdate(UUID bankAccountId) {
        return jpaRepository.findByIdForUpdate(bankAccountId).map(mapper::toDomain);
    }

    @Override
    public Optional<BankAccount> findByPayeeIdAndHash(UUID payeeId, String accountNumberHash) {
        return jpaRepository.findByPayeeIdAndAccountNumberHash(payeeId, accountNumberHash).map(mapper::toDomain);
    }

    @Override
    public Optional<BankAccount> findDefaultByPayeeIdAndStatuses(
            UUID payeeId,
            Collection<BankAccountStatus> statuses
    ) {
        return jpaRepository.findFirstByPayeeIdAndIsDefaultTrueAndStatusInOrderByCreatedAtDesc(payeeId, statuses)
                .map(mapper::toDomain);
    }

    @Override
    public List<BankAccount> findByPayeeId(UUID payeeId) {
        return jpaRepository.findByPayeeIdOrderByIsDefaultDescCreatedAtDesc(payeeId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void clearDefault(UUID payeeId, UUID excludedBankAccountId) {
        jpaRepository.clearDefault(payeeId, excludedBankAccountId);
    }
}
