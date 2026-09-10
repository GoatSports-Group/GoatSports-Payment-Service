package iuh.fit.paymentservice.application.dto.request.payment;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
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

    public void validate() {
        if (paymentId == null || collectorId == null) {
            throw new InvalidPaymentException(
                    "CASH_COLLECTION_REQUEST_INVALID", "Payment ID và collector ID là bắt buộc"
            );
        }
    }
}
