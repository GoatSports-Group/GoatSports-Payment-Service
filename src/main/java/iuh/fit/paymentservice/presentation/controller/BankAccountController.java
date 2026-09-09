package iuh.fit.paymentservice.presentation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.paymentservice.application.dto.request.bankaccount.LinkBankAccountCommand;
import iuh.fit.paymentservice.application.dto.request.security.EncryptedPayloadCommand;
import iuh.fit.paymentservice.application.dto.response.bankaccount.BankAccountResponseCommand;
import iuh.fit.paymentservice.application.dto.response.bankaccount.BankDirectoryEntry;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.client.BankDirectoryPort;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.port.security.EncryptedPayloadPort;
import iuh.fit.paymentservice.application.service.BankAccountService;
import iuh.fit.paymentservice.presentation.request.BankAccountIdPayload;
import iuh.fit.paymentservice.presentation.request.BankAccountPayload;
import iuh.fit.paymentservice.presentation.request.LinkBankAccountRequest;
import iuh.fit.paymentservice.presentation.response.EncryptionPublicKeyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final EncryptedPayloadPort encryptedPayloadPort;
    private final ObjectMapper objectMapper;

    @GetMapping("/bank-accounts/public-key")
    public EncryptionPublicKeyResponse publicKey() {
        return new EncryptionPublicKeyResponse(encryptedPayloadPort.getPublicKey());
    }

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
        BankAccountPayload payload = decrypt(request, BankAccountPayload.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(bankAccountService.link(
                new LinkBankAccountCommand(
                        userId,
                        payload.bankBin(),
                        payload.accountNumber(),
                        payload.accountName()
                )
        ));
    }

    @PatchMapping("/bank-accounts/default")
    public BankAccountResponseCommand makeDefault(@Valid @RequestBody LinkBankAccountRequest request) {
        BankAccountIdPayload payload = decrypt(request, BankAccountIdPayload.class);
        return bankAccountService.makeDefault(authenticatedUserId(), requireBankAccountId(payload));
    }

    @PatchMapping("/bank-accounts/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody LinkBankAccountRequest request) {
        BankAccountIdPayload payload = decrypt(request, BankAccountIdPayload.class);
        bankAccountService.disable(authenticatedUserId(), requireBankAccountId(payload));
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

    private <T> T decrypt(LinkBankAccountRequest request, Class<T> payloadType) {
        String decrypted = encryptedPayloadPort.decrypt(new EncryptedPayloadCommand(
                request.encryptedKey(), request.iv(), request.encryptedData()
        ));
        try {
            return objectMapper.readValue(decrypted, payloadType);
        } catch (JsonProcessingException exception) {
            throw new InvalidPaymentException(
                    "ENCRYPTED_PAYLOAD_INVALID",
                    "Dữ liệu tài khoản ngân hàng sau giải mã không hợp lệ"
            );
        }
    }

    private UUID requireBankAccountId(BankAccountIdPayload payload) {
        if (payload == null || payload.bankAccountId() == null) {
            throw new InvalidPaymentException(
                    "BANK_ACCOUNT_ID_REQUIRED",
                    "Bank account ID là bắt buộc"
            );
        }
        return payload.bankAccountId();
    }
}
