package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, insertable = false, updatable = false)
    private PaymentEntity payment;
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
    @Column(name = "auto_disburse", nullable = false)
    private Boolean autoDisburse;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RefundStatus status;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
    @Column(name = "last_reminded_at", insertable = false, updatable = false)
    private LocalDateTime lastRemindedAt;
}
