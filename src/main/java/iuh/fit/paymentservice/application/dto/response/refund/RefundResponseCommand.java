package iuh.fit.paymentservice.application.dto.response.refund;

import iuh.fit.paymentservice.domain.enums.RefundStatus;
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
public class RefundResponseCommand {
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
