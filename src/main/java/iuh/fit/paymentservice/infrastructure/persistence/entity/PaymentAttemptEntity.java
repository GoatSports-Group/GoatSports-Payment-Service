package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
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
@Table(name = "payment_attempts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAttemptEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentProvider provider;
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 32)
    private PaymentMethod method;
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;
    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;
    @Column(name = "deeplink", columnDefinition = "TEXT")
    private String deeplink;
    @Column(name = "qr_code_content", columnDefinition = "TEXT")
    private String qrCodeContent;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentAttemptStatus status;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
