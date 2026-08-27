package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderTransactionQueryCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderTransactionStatus;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.ProviderTransactionQueryPort;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MomoTransactionQueryAdapter implements ProviderTransactionQueryPort {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MomoProperties properties;
    private final MomoSignatureService signatureService;
    private final RestClient restClient;

    @Autowired
    public MomoTransactionQueryAdapter(
            MomoProperties properties,
            MomoSignatureService signatureService
    ) {
        this(
                properties,
                signatureService,
                RestClient.builder().requestFactory(requestFactory(properties)).build()
        );
    }

    MomoTransactionQueryAdapter(
            MomoProperties properties,
            MomoSignatureService signatureService,
            RestClient restClient
    ) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.restClient = restClient;
    }

    @Override
    public ProviderTransactionStatus query(ProviderTransactionQueryCommand command) {
        properties.requireEnabled();
        validateCommand(command);
        String reference = command.getAttemptId().toString().replace("-", "");
        String rawSignature = rawRequestSignature(reference, reference);
        MomoQueryResponse response = send(new MomoQueryRequest(
                properties.getPartnerCode(),
                reference,
                reference,
                signatureService.hmacSha256(properties.getSecretKey(), rawSignature),
                language(command.getLocale())
        ));
        validateResponse(response, reference, command.getExpectedAmount());
        return ProviderTransactionStatus.builder()
                .providerTransactionId(validTransactionId(response.transId()))
                .amount(response.amount())
                .resultCode(response.resultCode())
                .message(response.message())
                .occurredAt(providerTime(response.responseTime()))
                .build();
    }

    String rawRequestSignature(String orderId, String requestId) {
        return "accessKey=" + properties.getAccessKey()
                + "&orderId=" + orderId
                + "&partnerCode=" + properties.getPartnerCode()
                + "&requestId=" + requestId;
    }

    private MomoQueryResponse send(MomoQueryRequest request) {
        try {
            MomoQueryResponse response = restClient.post()
                    .uri(properties.getQueryUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MomoQueryResponse.class);
            if (response == null) {
                throw providerError("MOMO_QUERY_EMPTY_RESPONSE", "MoMo trả về response truy vấn rỗng");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new PaymentProviderException(
                    "MOMO_QUERY_HTTP_ERROR",
                    "MoMo trả về HTTP " + exception.getStatusCode().value() + " khi truy vấn giao dịch",
                    exception
            );
        } catch (RestClientException exception) {
            throw new PaymentProviderException(
                    "MOMO_QUERY_CONNECTION_ERROR",
                    "Không thể kết nối tới MoMo để truy vấn giao dịch",
                    exception
            );
        }
    }

    private void validateResponse(
            MomoQueryResponse response,
            String expectedReference,
            Double expectedAmount
    ) {
        if (response.resultCode() == null) {
            throw providerError("MOMO_QUERY_INVALID_RESPONSE", "MoMo không trả về resultCode khi truy vấn");
        }
        if (isTechnicalError(response.resultCode())) {
            throw providerError(
                    "MOMO_QUERY_RESULT_" + response.resultCode(),
                    "MoMo chưa thể xác nhận giao dịch: " + safeMessage(response.message())
            );
        }
        if (!properties.getPartnerCode().equals(response.partnerCode())
                || !expectedReference.equals(response.orderId())
                || !expectedReference.equals(response.requestId())
                || response.amount() == null
                || response.amount() != providerAmount(expectedAmount)) {
            throw providerError(
                    "MOMO_QUERY_RESPONSE_MISMATCH",
                    "Thông tin truy vấn từ MoMo không khớp với payment attempt"
            );
        }
    }

    private void validateCommand(ProviderTransactionQueryCommand command) {
        if (command == null || command.getAttemptId() == null || command.getExpectedAmount() == null) {
            throw providerError("MOMO_QUERY_INVALID", "Thiếu dữ liệu bắt buộc để truy vấn giao dịch MoMo");
        }
    }

    private boolean isTechnicalError(int resultCode) {
        return (resultCode > 0 && resultCode < 1_000) || resultCode == 99;
    }

    private long providerAmount(Double amount) {
        try {
            return BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (RuntimeException exception) {
            throw providerError("MOMO_QUERY_AMOUNT_INVALID", "Số tiền payment không phải số nguyên VND");
        }
    }

    private String validTransactionId(Long transId) {
        return transId == null || transId <= 0 ? null : transId.toString();
    }

    private LocalDateTime providerTime(Long responseTime) {
        if (responseTime == null || responseTime <= 0) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(responseTime), BUSINESS_ZONE);
        } catch (RuntimeException exception) {
            throw providerError("MOMO_QUERY_RESPONSE_TIME_INVALID", "responseTime truy vấn MoMo không hợp lệ");
        }
    }

    private String language(String locale) {
        return "en".equalsIgnoreCase(locale) ? "en" : "vi";
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "Không có mô tả" : message;
    }

    private PaymentProviderException providerError(String code, String message) {
        return new PaymentProviderException(code, message);
    }

    private static SimpleClientHttpRequestFactory requestFactory(MomoProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record MomoQueryRequest(
            String partnerCode,
            String requestId,
            String orderId,
            String signature,
            String lang
    ) {
    }

    private record MomoQueryResponse(
            String partnerCode,
            String requestId,
            String orderId,
            String extraData,
            Long amount,
            Long transId,
            String payType,
            Integer resultCode,
            String message,
            Long responseTime
    ) {
    }
}
