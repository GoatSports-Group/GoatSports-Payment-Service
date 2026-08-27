package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;

import java.util.UUID;

public interface ProviderRefundQueryPort {
    ProviderRefundResult query(UUID refundId);
}
