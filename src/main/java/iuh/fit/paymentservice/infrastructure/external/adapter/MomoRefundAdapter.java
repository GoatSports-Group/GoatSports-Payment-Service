package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.refund.ProviderRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundStatus;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentRefundPort;
import iuh.fit.paymentservice.application.port.client.ProviderRefundQueryPort;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class MomoRefundAdapter implements PaymentRefundPort, ProviderRefundQueryPort {
    private static final long MIN_AMOUNT = 1_000L;
    private static final long MAX_AMOUNT = 50_000_000L;

    private final MomoProperties properties;
    private final MomoSignatureService signatureService;
    private final RestClient restClient;

    @Autowired
    public MomoRefundAdapter(MomoProperties properties, MomoSignatureService signatureService) {
        this(properties, signatureService,
                RestClient.builder().requestFactory(requestFactory(properties)).build());
    }

    MomoRefundAdapter(MomoProperties properties, MomoSignatureService signatureService, RestClient restClient) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.restClient = restClient;
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        properties.requireEnabled();
        validateCommand(command);
        String reference = reference(command.getRefundId());
        long amount = providerAmount(command.getAmount());
        long originalTransId = providerTransactionId(command.getProviderTransactionId());
        String description = description(command.getDescription());
        String rawSignature = "accessKey=" + properties.getAccessKey()
                + "&amount=" + amount
                + "&description=" + description
                + "&orderId=" + reference
                + "&partnerCode=" + properties.getPartnerCode()
                + "&requestId=" + reference
                + "&transId=" + originalTransId;
        MomoRefundRequest request = new MomoRefundRequest(
                properties.getPartnerCode(), reference, reference, amount, originalTransId,
                language(command.getLocale()), description,
                signatureService.hmacSha256(properties.getSecretKey(), rawSignature)
        );
        MomoRefundResponse response = post(properties.getRefundUrl(), request,
                MomoRefundResponse.class, "refund");
        validateIdentity(response.partnerCode(), response.orderId(), response.requestId(), reference, reference);
        if (response.amount() == null || response.amount() != amount) {
            throw providerError("MOMO_REFUND_RESPONSE_MISMATCH", "Số tiền refund MoMo không khớp request");
        }
        return result(response.resultCode(), response.transId(), response.message());
    }

    @Override
    public ProviderRefundResult query(UUID refundId) {
        properties.requireEnabled();
        if (refundId == null) {
            throw providerError("MOMO_REFUND_REFERENCE_REQUIRED", "Thiếu mã refund để query MoMo");
        }
        String reference = reference(refundId);
        String requestId = "Q" + UUID.randomUUID().toString().replace("-", "");
        String rawSignature = "accessKey=" + properties.getAccessKey()
                + "&orderId=" + reference
                + "&partnerCode=" + properties.getPartnerCode()
                + "&requestId=" + requestId;
        MomoRefundQueryRequest request = new MomoRefundQueryRequest(
                properties.getPartnerCode(), requestId, reference, "vi",
                signatureService.hmacSha256(properties.getSecretKey(), rawSignature)
        );
        MomoRefundQueryResponse response = post(properties.getRefundQueryUrl(), request,
                MomoRefundQueryResponse.class, "refund query");
        validateIdentity(response.partnerCode(), response.orderId(), response.requestId(), reference, requestId);
        MomoRefundTransaction transaction = response.refundTrans() == null ? null
                : response.refundTrans().stream()
                .filter(item -> reference.equals(item.orderId()))
                .findFirst()
                .orElse(null);
        if (transaction != null) {
            return result(transaction.resultCode(), transaction.transId(), response.message());
        }
        if (response.resultCode() != null && response.resultCode() != 0) {
            return result(response.resultCode(), null, response.message());
        }
        return ProviderRefundResult.builder()
                .status(ProviderRefundStatus.PENDING)
                .failureReason("MoMo chưa trả về giao dịch refund trong kết quả đối soát")
                .build();
    }

    private <T> T post(String url, Object request, Class<T> responseType, String operation) {
        try {
            T response = restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(responseType);
            if (response == null) {
                throw providerError("MOMO_EMPTY_RESPONSE", "MoMo trả về response rỗng khi " + operation);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new PaymentProviderException("MOMO_HTTP_ERROR",
                    "MoMo trả về HTTP " + exception.getStatusCode().value() + " khi " + operation, exception);
        } catch (RestClientException exception) {
            throw new PaymentProviderException("MOMO_CONNECTION_ERROR",
                    "Không thể kết nối tới MoMo khi " + operation, exception);
        }
    }

    private ProviderRefundResult result(Integer resultCode, Long transId, String message) {
        if (resultCode == null) {
            throw providerError("MOMO_INVALID_RESPONSE", "MoMo không trả về resultCode cho refund");
        }
        ProviderRefundStatus status = resultCode == 0
                ? ProviderRefundStatus.SUCCEEDED
                : resultCode == 40 || resultCode == 41 || resultCode == 43
                || resultCode == 1000 || resultCode == 7000 || resultCode == 7002
                || resultCode == 9000
                ? ProviderRefundStatus.PENDING
                : resultCode == 1080 || resultCode == 1081 || resultCode == 1088
                ? ProviderRefundStatus.FAILED
                : ProviderRefundStatus.MANUAL_REVIEW;
        if (status == ProviderRefundStatus.SUCCEEDED && (transId == null || transId == 0)) {
            throw providerError(
                    "MOMO_REFUND_TRANSACTION_ID_REQUIRED",
                    "MoMo không trả về transId hợp lệ cho refund thành công"
            );
        }
        return ProviderRefundResult.builder()
                .status(status)
                .providerRefundId(transId == null || transId == 0 ? null : Long.toString(transId))
                .failureReason(status == ProviderRefundStatus.SUCCEEDED ? null
                        : "MOMO_RESULT_" + resultCode + ": " + safeMessage(message))
                .build();
    }

    private void validateIdentity(
            String partnerCode,
            String orderId,
            String requestId,
            String expectedOrderId,
            String expectedRequestId
    ) {
        if (!properties.getPartnerCode().equals(partnerCode)
                || !expectedOrderId.equals(orderId) || !expectedRequestId.equals(requestId)) {
            throw providerError("MOMO_REFUND_RESPONSE_MISMATCH",
                    "Thông tin định danh refund MoMo không khớp request");
        }
    }

    private void validateCommand(ProviderRefundCommand command) {
        if (command == null || command.getRefundId() == null) {
            throw providerError("MOMO_REFUND_REQUEST_INVALID", "Thiếu mã refund");
        }
    }

    private long providerAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount)) {
            throw providerError("MOMO_INVALID_AMOUNT", "Số tiền refund MoMo không hợp lệ");
        }
        try {
            long value = BigDecimal.valueOf(amount).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (value < MIN_AMOUNT || value > MAX_AMOUNT) {
                throw providerError("MOMO_INVALID_AMOUNT",
                        "Số tiền refund MoMo phải nằm trong khoảng 1.000-50.000.000 VND");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new PaymentProviderException("MOMO_INVALID_AMOUNT",
                    "Số tiền refund MoMo phải là số nguyên VND", exception);
        }
    }

    private long providerTransactionId(String value) {
        try {
            long transactionId = Long.parseLong(value);
            if (transactionId <= 0) {
                throw new NumberFormatException();
            }
            return transactionId;
        } catch (RuntimeException exception) {
            throw new PaymentProviderException("MOMO_TRANSACTION_ID_INVALID",
                    "Payment không có transId MoMo hợp lệ để refund", exception);
        }
    }

    private String description(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw providerError("MOMO_REFUND_DESCRIPTION_INVALID", "Mô tả refund MoMo không hợp lệ");
        }
        return value;
    }

    private String language(String value) {
        return "en".equalsIgnoreCase(value) ? "en" : "vi";
    }

    private String reference(UUID refundId) {
        return refundId.toString().replace("-", "");
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "Không có mô tả" : message;
    }

    private PaymentProviderException providerError(String code, String message) {
        return new PaymentProviderException(code, message);
    }

    private static SimpleClientHttpRequestFactory requestFactory(MomoProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record MomoRefundRequest(String partnerCode, String orderId, String requestId,
                                     long amount, long transId, String lang,
                                     String description, String signature) {
    }

    private record MomoRefundResponse(String partnerCode, String orderId, String requestId,
                                      Long amount, Long transId, Integer resultCode,
                                      String message, Long responseTime) {
    }

    private record MomoRefundQueryRequest(String partnerCode, String requestId, String orderId,
                                          String lang, String signature) {
    }

    private record MomoRefundQueryResponse(String partnerCode, String orderId, String requestId,
                                           Integer resultCode, String message, Long responseTime,
                                           List<MomoRefundTransaction> refundTrans) {
    }

    private record MomoRefundTransaction(String orderId, Long amount, Integer resultCode,
                                         Long transId, Long createdTime) {
    }
}
