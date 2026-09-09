package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.bankaccount.LinkBankAccountCommand;
import iuh.fit.paymentservice.application.dto.response.bankaccount.BankAccountResponseCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentAccessDeniedException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.application.port.security.BankAccountProtector;
import iuh.fit.paymentservice.application.service.BankAccountService;
import iuh.fit.paymentservice.application.service.RefundDisbursementService;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {
    private static final Pattern BIN_PATTERN = Pattern.compile("\\d{6,12}");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\d{6,19}");

    private final BankAccountRepository bankAccountRepository;
    private final BankAccountProtector bankAccountProtector;
    private final RefundDisbursementService refundDisbursementService;

    @Override
    public List<BankAccountResponseCommand> getMyAccounts(UUID payeeId) {
        return bankAccountRepository.findByPayeeId(payeeId).stream().map(this::response).toList();
    }

    @Override
    @Transactional
    public BankAccountResponseCommand link(LinkBankAccountCommand command) {
        validate(command);
        String bankBin = command.bankBin().trim();
        String accountNumber = command.accountNumber().replaceAll("\\s+", "");
        String hash = bankAccountProtector.hash(bankBin, accountNumber);
        BankAccount existing = bankAccountRepository.findByPayeeIdAndHash(command.payeeId(), hash).orElse(null);
        if (existing != null
                && existing.getStatus() != BankAccountStatus.DISABLED
                && existing.getStatus() != BankAccountStatus.REJECTED) {
            throw new PaymentConflictException(
                    "BANK_ACCOUNT_ALREADY_LINKED",
                    "Tài khoản ngân hàng này đã được liên kết"
            );
        }

        boolean firstActiveAccount = bankAccountRepository.findByPayeeId(command.payeeId()).stream()
                .noneMatch(account -> account.getStatus() == BankAccountStatus.PENDING_VERIFICATION
                        || account.getStatus() == BankAccountStatus.VERIFIED);
        BankAccount candidate = existing == null ? BankAccount.builder()
                .payeeId(command.payeeId()).bankBin(bankBin).accountNumberHash(hash).build() : existing;
        candidate.setAccountNumberEncrypted(bankAccountProtector.encrypt(accountNumber));
        candidate.setAccountNumberLast4(accountNumber.substring(accountNumber.length() - 4));
        candidate.setAccountName(normalizeName(command.accountName()));
        candidate.setStatus(BankAccountStatus.PENDING_VERIFICATION);
        candidate.setIsDefault(firstActiveAccount || Boolean.TRUE.equals(candidate.getIsDefault()));
        candidate.setVerifiedAt(null);
        candidate.setDisabledAt(null);
        BankAccount saved = bankAccountRepository.save(candidate);

        scheduleAutomaticRefunds(command.payeeId());
        return response(saved);
    }

    @Override
    @Transactional
    public BankAccountResponseCommand makeDefault(UUID payeeId, UUID bankAccountId) {
        BankAccount account = ownedAccount(payeeId, bankAccountId);
        if (account.getStatus() == BankAccountStatus.DISABLED || account.getStatus() == BankAccountStatus.REJECTED) {
            throw new PaymentConflictException("BANK_ACCOUNT_INACTIVE", "Tài khoản ngân hàng không còn hoạt động");
        }
        bankAccountRepository.clearDefault(payeeId, bankAccountId);
        account.setIsDefault(true);
        return response(bankAccountRepository.save(account));
    }

    @Override
    @Transactional
    public void disable(UUID payeeId, UUID bankAccountId) {
        BankAccount account = ownedAccount(payeeId, bankAccountId);
        account.setStatus(BankAccountStatus.DISABLED);
        account.setIsDefault(false);
        account.setDisabledAt(LocalDateTime.now());
        bankAccountRepository.save(account);
    }

    private BankAccount ownedAccount(UUID payeeId, UUID bankAccountId) {
        BankAccount account = bankAccountRepository.findByIdForUpdate(bankAccountId)
                .orElseThrow(() -> new PaymentConflictException("BANK_ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản ngân hàng"));
        if (!account.getPayeeId().equals(payeeId)) {
            throw new PaymentAccessDeniedException();
        }
        return account;
    }

    private void validate(LinkBankAccountCommand command) {
        if (command == null || command.payeeId() == null
                || command.bankBin() == null || !BIN_PATTERN.matcher(command.bankBin().trim()).matches()
                || command.accountNumber() == null
                || !ACCOUNT_PATTERN.matcher(command.accountNumber().replaceAll("\\s+", "")).matches()
                || command.accountName() == null || command.accountName().isBlank()
                || command.accountName().trim().length() > 255) {
            throw new InvalidPaymentException(
                    "BANK_ACCOUNT_INVALID",
                    "Ngân hàng, số tài khoản và tên chủ tài khoản không hợp lệ"
            );
        }
    }

    private String normalizeName(String accountName) {
        return Normalizer.normalize(accountName.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private void scheduleAutomaticRefunds(UUID payeeId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refundDisbursementService.processWaitingAutomaticRefunds(payeeId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    refundDisbursementService.processWaitingAutomaticRefunds(payeeId);
                } catch (RuntimeException ignored) {
                    // Refund remains reserved and will be retried; linking the account must stay successful.
                }
            }
        });
    }

    private BankAccountResponseCommand response(BankAccount account) {
        return new BankAccountResponseCommand(
                account.getBankAccountId(), account.getBankBin(), account.getAccountNumberLast4(),
                account.getAccountName(), account.getStatus(), account.getIsDefault(),
                account.getVerifiedAt(), account.getCreatedAt()
        );
    }
}
