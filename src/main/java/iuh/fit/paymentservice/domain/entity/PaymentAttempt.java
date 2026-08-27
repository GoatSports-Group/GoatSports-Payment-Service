package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
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
public class PaymentAttempt extends BaseEntity {
    private UUID attemptId;
    private UUID paymentId;
    private PaymentProvider provider;
    private PaymentMethod method;
    private String providerTransactionId;
    private String checkoutUrl;
    private PaymentAttemptStatus status;
    private String failureCode;
    private String failureReason;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
}
