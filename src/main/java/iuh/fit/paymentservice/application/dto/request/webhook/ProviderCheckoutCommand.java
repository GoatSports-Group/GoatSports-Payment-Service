package iuh.fit.paymentservice.application.dto.request.webhook;

import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProviderCheckoutCommand {
    private UUID attemptId;
    private Long orderCode;
    private Double amount;
    private String currency;
    private String orderInfo;
    private String ipAddress;
    private String locale;
    private PaymentMethod method;
    private LocalDateTime initiatedAt;
    private LocalDateTime paymentExpiresAt;
}
