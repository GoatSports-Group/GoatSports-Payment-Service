package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.response.payment.ProviderPaymentStatusResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentStatusProviderPort;
import iuh.fit.paymentservice.infrastructure.external.config.PayOsClientProvider;
import iuh.fit.paymentservice.infrastructure.external.config.PayOsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

@Component
@RequiredArgsConstructor
public class PayOsPaymentStatusAdapter implements PaymentStatusProviderPort {
    private final PayOsProperties properties;
    private final PayOsClientProvider clientProvider;

    @Override
    public ProviderPaymentStatusResult get(String providerTransactionId) {
        properties.requireEnabled();
        try {
            return toResult(clientProvider.get().paymentRequests().get(providerTransactionId));
        } catch (RuntimeException exception) {
            throw providerError("PAYOS_QUERY_ERROR", "Không thể đối soát giao dịch với payOS", exception);
        }
    }

    @Override
    public ProviderPaymentStatusResult cancel(String providerTransactionId, String reason) {
        properties.requireEnabled();
        try {
            return toResult(clientProvider.get().paymentRequests().cancel(providerTransactionId, reason));
        } catch (RuntimeException exception) {
            throw providerError("PAYOS_CANCEL_ERROR", "Không thể đóng liên kết thanh toán payOS", exception);
        }
    }

    private ProviderPaymentStatusResult toResult(PaymentLink paymentLink) {
        if (paymentLink == null || paymentLink.getId() == null || paymentLink.getStatus() == null) {
            throw new PaymentProviderException(
                    "PAYOS_INVALID_STATUS_RESPONSE",
                    "payOS trả về trạng thái giao dịch không hợp lệ"
            );
        }
        return new ProviderPaymentStatusResult(
                paymentLink.getId(),
                paymentLink.getOrderCode(),
                paymentLink.getAmount(),
                paymentLink.getAmountPaid(),
                mapStatus(paymentLink.getStatus()),
                failureCode(paymentLink.getStatus()),
                failureReason(paymentLink)
        );
    }

    private ProviderPaymentStatusResult.Status mapStatus(PaymentLinkStatus status) {
        return switch (status) {
            case PENDING -> ProviderPaymentStatusResult.Status.PENDING;
            case PROCESSING -> ProviderPaymentStatusResult.Status.PROCESSING;
            case PAID -> ProviderPaymentStatusResult.Status.PAID;
            case CANCELLED -> ProviderPaymentStatusResult.Status.CANCELLED;
            case EXPIRED -> ProviderPaymentStatusResult.Status.EXPIRED;
            case FAILED -> ProviderPaymentStatusResult.Status.FAILED;
            case UNDERPAID -> ProviderPaymentStatusResult.Status.UNDERPAID;
        };
    }

    private String failureCode(PaymentLinkStatus status) {
        return switch (status) {
            case CANCELLED -> "PAYOS_CANCELLED";
            case EXPIRED -> "PAYOS_EXPIRED";
            case FAILED -> "PAYOS_FAILED";
            case UNDERPAID -> "PAYOS_UNDERPAID";
            default -> null;
        };
    }

    private String failureReason(PaymentLink paymentLink) {
        return switch (paymentLink.getStatus()) {
            case CANCELLED -> paymentLink.getCancellationReason() == null
                    || paymentLink.getCancellationReason().isBlank()
                    ? "Người dùng đã hủy thanh toán trên payOS"
                    : paymentLink.getCancellationReason();
            case EXPIRED -> "Liên kết thanh toán payOS đã hết hạn";
            case FAILED -> "payOS không thể hoàn tất giao dịch";
            case UNDERPAID -> "Số tiền chuyển khoản chưa đủ so với số tiền cần thanh toán";
            default -> null;
        };
    }

    private PaymentProviderException providerError(String code, String message, RuntimeException exception) {
        if (exception instanceof PaymentProviderException providerException) {
            return providerException;
        }
        return new PaymentProviderException(code, message, exception);
    }
}
