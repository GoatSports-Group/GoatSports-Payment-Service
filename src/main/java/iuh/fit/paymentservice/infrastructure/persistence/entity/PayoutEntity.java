package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payouts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;
    @Column(name = "settlement_id", nullable = false, unique = true)
    private UUID settlementId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false, insertable = false, updatable = false)
    private SettlementEntity settlement;
    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false, insertable = false, updatable = false)
    private BankAccountEntity bankAccount;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentProvider provider;
    @Column(name = "provider_payout_id", length = 100)
    private String providerPayoutId;
    @Column(name = "reference_id", nullable = false, length = 128)
    private String referenceId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "amount", nullable = false)
    private Double amount;
    @Column(name = "destination_bank_bin", nullable = false, length = 12)
    private String destinationBankBin;
    @Column(name = "destination_account_masked", nullable = false, length = 64)
    private String destinationAccountMasked;
    @Column(name = "destination_account_name", nullable = false, length = 255)
    private String destinationAccountName;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PayoutStatus status;
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
