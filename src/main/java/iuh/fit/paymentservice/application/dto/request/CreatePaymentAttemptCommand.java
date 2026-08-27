package iuh.fit.paymentservice.application.dto.request;

import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentAttemptCommand {
    private UUID paymentId;
    private UUID payerId;
    private PaymentMethod method;
    private String locale;
    private String ipAddress;
}
