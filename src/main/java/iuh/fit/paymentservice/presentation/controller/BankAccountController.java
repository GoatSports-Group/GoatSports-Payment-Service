package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.dto.request.bankaccount.LinkBankAccountCommand;
import iuh.fit.paymentservice.application.dto.response.bankaccount.BankAccountResponseCommand;
import iuh.fit.paymentservice.application.dto.response.bankaccount.BankDirectoryEntry;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.client.BankDirectoryPort;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.BankAccountService;
import iuh.fit.paymentservice.presentation.request.LinkBankAccountRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService bankAccountService;
    private final BankDirectoryPort bankDirectoryPort;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/banks")
    public List<BankDirectoryEntry> banks() {
        return bankDirectoryPort.getBanks();
    }

    @GetMapping("/bank-accounts/me")
    public List<BankAccountResponseCommand> myAccounts() {
        return bankAccountService.getMyAccounts(authenticatedUserId());
    }

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankAccountResponseCommand> link(@Valid @RequestBody LinkBankAccountRequest request) {
        UUID userId = authenticatedUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(bankAccountService.link(
                new LinkBankAccountCommand(userId, request.bankBin(), request.accountNumber(), request.accountName())
        ));
    }

    @PatchMapping("/bank-accounts/{bankAccountId}/default")
    public BankAccountResponseCommand makeDefault(@PathVariable UUID bankAccountId) {
        return bankAccountService.makeDefault(authenticatedUserId(), bankAccountId);
    }

    @DeleteMapping("/bank-accounts/{bankAccountId}")
    public ResponseEntity<Void> disable(@PathVariable UUID bankAccountId) {
        bankAccountService.disable(authenticatedUserId(), bankAccountId);
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId() {
        String userId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_USER_REQUIRED",
                        "Gateway không cung cấp user ID hợp lệ"
                ));
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException("AUTHENTICATED_USER_REQUIRED", "Gateway không cung cấp user ID hợp lệ");
        }
    }
}
