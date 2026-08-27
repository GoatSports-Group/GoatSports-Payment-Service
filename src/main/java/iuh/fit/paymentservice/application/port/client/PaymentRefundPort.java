package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.request.refund.ProviderRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;

public interface PaymentRefundPort {
    ProviderRefundResult refund(ProviderRefundCommand command);
}
