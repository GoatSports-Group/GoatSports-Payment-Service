package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkBankAccountRequest(
        @NotBlank @Size(max = 512) String encryptedKey,
        @NotBlank @Size(max = 64) String iv,
        @NotBlank @Size(max = 4096) String encryptedData
) {
}
