package iuh.fit.paymentservice.application.dto.request.refund;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRefundCommand {
    private UUID paymentId;
    private UUID requestedBy;
    private String reason;
    private Double amount;
    private String idempotencyKey;
    private Boolean autoDisburse;
}
