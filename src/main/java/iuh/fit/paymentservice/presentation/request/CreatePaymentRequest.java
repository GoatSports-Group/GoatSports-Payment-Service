package iuh.fit.paymentservice.presentation.request;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    @NotNull
    private PaymentReferenceType referenceType;
    @NotNull
    private UUID referenceId;
    @NotNull
    private PaymentPurpose purpose;
    @NotNull
    @DecimalMin("0.01")
    private Double amount;
    @NotBlank
    @Pattern(regexp = "(?i)[A-Z]{3}")
    private String currency;
    private String description;
    private LocalDateTime expiresAt;
}
