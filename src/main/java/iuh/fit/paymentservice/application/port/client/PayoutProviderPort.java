package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.payout.CreatePayoutCommand;
import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;

public interface PayoutProviderPort {
    ProviderPayoutResult create(CreatePayoutCommand command);

    ProviderPayoutResult get(String providerPayoutId);
}
