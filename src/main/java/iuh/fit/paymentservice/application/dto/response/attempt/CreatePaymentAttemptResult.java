package iuh.fit.paymentservice.application.dto.response.attempt;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreatePaymentAttemptResult {
    private final PaymentAttemptResponseCommand paymentAttempt;
    private final boolean replayed;
}
