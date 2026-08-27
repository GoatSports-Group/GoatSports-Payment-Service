package iuh.fit.paymentservice.application.dto.request.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProviderTransactionQueryCommand {
    private UUID attemptId;
    private Double expectedAmount;
    private String locale;
}
