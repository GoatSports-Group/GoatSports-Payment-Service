package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlements")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;
    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "gross_amount", nullable = false)
    private Double grossAmount;
    @Column(name = "refund_amount", nullable = false)
    private Double refundAmount;
    @Column(name = "platform_fee_amount", nullable = false)
    private Double platformFeeAmount;
    @Column(name = "payout_amount", nullable = false)
    private Double payoutAmount;
    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SettlementStatus status;
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
