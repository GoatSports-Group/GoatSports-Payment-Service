package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LinkBankAccountRequest(
        @NotBlank @Pattern(regexp = "\\d{6,12}") String bankBin,
        @NotBlank @Pattern(regexp = "\\d{6,19}") String accountNumber,
        @NotBlank @Size(max = 255) String accountName
) {
}
