package iuh.fit.paymentservice.application.dto.request.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CollectCashPaymentCommand {
    private UUID paymentId;
    private UUID collectorId;
}
