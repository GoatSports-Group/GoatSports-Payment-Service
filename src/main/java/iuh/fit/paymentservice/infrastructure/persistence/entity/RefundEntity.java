package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.RefundStatus;
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
@Table(name = "refunds")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefundEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refund_id", nullable = false)
    private UUID refundId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @Column(name = "amount", nullable = false)
    private Double amount;
    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RefundStatus status;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
