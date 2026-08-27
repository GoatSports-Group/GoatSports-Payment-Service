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
public class ProviderRefundCommand {
    private UUID refundId;
    private Double amount;
    private String providerTransactionId;
    private String description;
    private String locale;
}
