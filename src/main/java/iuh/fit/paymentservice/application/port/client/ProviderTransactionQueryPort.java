package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderTransactionQueryCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderTransactionStatus;

public interface ProviderTransactionQueryPort {
    ProviderTransactionStatus query(ProviderTransactionQueryCommand command);
}
