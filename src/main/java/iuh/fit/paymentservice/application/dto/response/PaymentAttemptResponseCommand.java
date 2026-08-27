package iuh.fit.paymentservice.application.dto.response;

import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
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
public class PaymentAttemptResponseCommand {
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
