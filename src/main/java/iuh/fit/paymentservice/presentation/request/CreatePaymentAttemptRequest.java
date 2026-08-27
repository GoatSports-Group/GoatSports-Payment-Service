package iuh.fit.paymentservice.presentation.request;

import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentAttemptRequest {
    @NotNull
    private PaymentMethod method;
    @Pattern(regexp = "(?i)vn|en", message = "Locale phải là vn hoặc en")
    private String locale;
}
