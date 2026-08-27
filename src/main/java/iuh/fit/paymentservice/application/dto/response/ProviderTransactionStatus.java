package iuh.fit.paymentservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ProviderTransactionStatus {
    private String providerTransactionId;
    private Long amount;
    private Integer resultCode;
    private String message;
    private LocalDateTime occurredAt;
}
