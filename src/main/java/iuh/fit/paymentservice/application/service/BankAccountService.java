package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.bankaccount.LinkBankAccountCommand;
import iuh.fit.paymentservice.application.dto.response.bankaccount.BankAccountResponseCommand;

import java.util.List;
import java.util.UUID;

public interface BankAccountService {
    List<BankAccountResponseCommand> getMyAccounts(UUID payeeId);

    BankAccountResponseCommand link(LinkBankAccountCommand command);

    BankAccountResponseCommand makeDefault(UUID payeeId, UUID bankAccountId);

    void disable(UUID payeeId, UUID bankAccountId);
}
