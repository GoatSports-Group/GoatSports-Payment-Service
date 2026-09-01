package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Future;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.time.LocalDateTime;

@Getter
@Setter
public class CreateBookingCheckoutRequest {
    @NotNull
    private UUID bookingId;
    @NotNull
    @DecimalMin("0.01")
    private Double amount;
    @NotBlank
    @Pattern(regexp = "(?i)[A-Z]{3}")
    private String currency;
    @Pattern(regexp = "(?i)vn|en")
    private String locale = "vn";
    @NotNull
    @Future
    private LocalDateTime expiresAt;
}
