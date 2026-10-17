package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.RefundStatus;
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
public class Refund extends BaseEntity {
    private UUID refundId;
    private UUID paymentId;
    private UUID requestedBy;
    private String reason;
    private Double amount;
    private String providerRefundId;
    private String idempotencyKey;
    private Boolean autoDisburse;
    private RefundStatus status;
    private String failureReason;
    private LocalDateTime refundedAt;
    @lombok.Builder.Default
    private Integer retryCount = 0;
    private LocalDateTime lastRemindedAt;
}
