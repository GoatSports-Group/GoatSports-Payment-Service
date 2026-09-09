package iuh.fit.paymentservice.application.dto.response.payment;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
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
public class PaymentResponseCommand {
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
    private PaymentProvider provider;
    private PaymentMethod method;
    private String providerTransactionId;
    private String failureCode;
    private String failureReason;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
