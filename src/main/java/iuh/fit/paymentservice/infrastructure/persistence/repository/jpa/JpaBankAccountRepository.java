package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.BankAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaBankAccountRepository extends JpaRepository<BankAccountEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BankAccountEntity account where account.bankAccountId = :bankAccountId")
    Optional<BankAccountEntity> findByIdForUpdate(UUID bankAccountId);

    Optional<BankAccountEntity> findByPayeeIdAndAccountNumberHash(UUID payeeId, String accountNumberHash);

    Optional<BankAccountEntity> findFirstByPayeeIdAndIsDefaultTrueAndStatusInOrderByCreatedAtDesc(
            UUID payeeId,
            Collection<BankAccountStatus> statuses
    );

    List<BankAccountEntity> findByPayeeIdOrderByIsDefaultDescCreatedAtDesc(UUID payeeId);

    @Modifying
    @Query("update BankAccountEntity account set account.isDefault = false "
            + "where account.payeeId = :payeeId and account.bankAccountId <> :excludedBankAccountId")
    void clearDefault(UUID payeeId, UUID excludedBankAccountId);
}
