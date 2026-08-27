package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends BaseEntity {
    private UUID paymentId;
    private UUID payerId;
    private PaymentPurpose purpose;
    private PaymentReferenceType referenceType;
    private UUID referenceId;
    private Double amount;
    private String currency;
    private String description;
    private String idempotencyKey;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;
}
