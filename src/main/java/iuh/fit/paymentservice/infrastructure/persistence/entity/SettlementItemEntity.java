package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.SettlementItemType;
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

import java.util.UUID;

@Entity
@Table(name = "settlement_items")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementItemEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_item_id", nullable = false)
    private UUID settlementItemId;
    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false, insertable = false, updatable = false)
    private SettlementEntity settlement;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, insertable = false, updatable = false)
    private PaymentEntity payment;
    @Column(name = "refund_id")
    private UUID refundId;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", insertable = false, updatable = false)
    private RefundEntity refund;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private SettlementItemType type;
    @Column(name = "amount", nullable = false)
    private Double amount;
}
