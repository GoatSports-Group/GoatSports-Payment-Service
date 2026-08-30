package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CollectBookingCashRequest {
    @NotNull
    private UUID payerId;
    @NotNull
    private UUID bookingId;
    @NotNull
    @DecimalMin("0.01")
    private Double amount;
    @NotBlank
    @Pattern(regexp = "(?i)[A-Z]{3}")
    private String currency;
}
