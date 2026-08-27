package iuh.fit.paymentservice.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProviderPaymentResultCommand {
    private UUID attemptId;
    private String providerTransactionId;
    private Long amount;
    private Integer resultCode;
    private String message;
    private LocalDateTime occurredAt;
}
