package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
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
@Table(name = "payments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "payer_id", nullable = false)
    private UUID payerId;
    @Column(name = "payee_id")
    private UUID payeeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private PaymentPurpose purpose;
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 40)
    private PaymentReferenceType referenceType;
    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;
    @Column(name = "amount", nullable = false)
    private Double amount;
    @Column(name = "platform_fee_amount", nullable = false)
    private Double platformFeeAmount;
    @Column(name = "payee_amount", nullable = false)
    private Double payeeAmount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "description", length = 500)
    private String description;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentStatus status;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
