package iuh.fit.paymentservice.application.dto.response.refund;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRefundResult {
    private ProviderRefundStatus status;
    private String providerRefundId;
    private String failureReason;
}
