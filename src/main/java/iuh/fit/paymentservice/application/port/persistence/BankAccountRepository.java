package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository {
    BankAccount save(BankAccount bankAccount);

    Optional<BankAccount> findByIdForUpdate(UUID bankAccountId);

    Optional<BankAccount> findByPayeeIdAndHash(UUID payeeId, String accountNumberHash);

    Optional<BankAccount> findDefaultByPayeeIdAndStatuses(UUID payeeId, Collection<BankAccountStatus> statuses);

    List<BankAccount> findByPayeeId(UUID payeeId);

    void clearDefault(UUID payeeId, UUID excludedBankAccountId);
}
