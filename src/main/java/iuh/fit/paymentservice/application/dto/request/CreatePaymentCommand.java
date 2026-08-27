package iuh.fit.paymentservice.application.dto.request;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
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
public class CreatePaymentCommand {
    private UUID payerId;
    private PaymentReferenceType referenceType;
    private UUID referenceId;
    private PaymentPurpose purpose;
    private Double amount;
    private String currency;
    private String description;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
}
