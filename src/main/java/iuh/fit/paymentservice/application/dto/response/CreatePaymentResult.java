package iuh.fit.paymentservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreatePaymentResult {
    private final PaymentResponseCommand payment;
    private final boolean replayed;
}
