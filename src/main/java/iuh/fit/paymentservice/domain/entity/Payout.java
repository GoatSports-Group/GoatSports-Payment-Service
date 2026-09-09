package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
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
public class Payout extends BaseEntity {
    private UUID payoutId;
    private UUID settlementId;
    private UUID refundId;
    private UUID bankAccountId;
    private PaymentProvider provider;
    private String providerPayoutId;
    private String referenceId;
    private String idempotencyKey;
    private Double amount;
    private String destinationBankBin;
    private String destinationAccountMasked;
    private String destinationAccountName;
    private PayoutStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String failureCode;
    private String failureReason;
}
