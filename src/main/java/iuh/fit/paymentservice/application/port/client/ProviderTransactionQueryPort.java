package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.ProviderTransactionQueryCommand;
import iuh.fit.paymentservice.application.dto.response.ProviderTransactionStatus;

public interface ProviderTransactionQueryPort {
    ProviderTransactionStatus query(ProviderTransactionQueryCommand command);
}
