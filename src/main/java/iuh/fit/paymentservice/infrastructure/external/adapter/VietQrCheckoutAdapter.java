package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.ProviderCheckoutResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.infrastructure.external.config.VietQrProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class VietQrCheckoutAdapter implements PaymentCheckoutPort {
    private static final long MAX_AMOUNT = 9_999_999_999_999L;

    private final VietQrProperties properties;
    private final RestClient restClient;

    @Autowired
    public VietQrCheckoutAdapter(VietQrProperties properties) {
        this(properties, RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build());
    }

    VietQrCheckoutAdapter(VietQrProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public ProviderCheckoutResult createCheckout(ProviderCheckoutCommand command) {
        properties.requireEnabled();
        if (command.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new PaymentProviderException(
                    "UNSUPPORTED_PAYMENT_METHOD",
                    "VietQR chỉ hỗ trợ phương thức BANK_TRANSFER"
            );
        }

        long amount = providerAmount(command.getAmount());
        String addInfo = transferContent(command);
        if (!properties.hasApiCredentials()) {
            return new ProviderCheckoutResult(null, null, quickLink(amount, addInfo));
        }

        VietQrGenerateRequest request = new VietQrGenerateRequest(
                properties.getAccountNo(),
                properties.getAccountName(),
                properties.getAcqId(),
                amount,
                addInfo,
                "text",
                properties.getTemplate()
        );
        VietQrGenerateResponse response = send(request);
        if (!"00".equals(response.code()) || response.data() == null) {
            throw new PaymentProviderException(
                    "VIETQR_RESULT_" + safeCode(response.code()),
                    "VietQR từ chối tạo mã: " + safeDescription(response.desc())
            );
        }
        if (response.data().qrDataURL() == null
                || !response.data().qrDataURL().startsWith("data:image/png;base64,")) {
            throw new PaymentProviderException(
                    "VIETQR_INVALID_RESPONSE",
                    "VietQR không trả về ảnh QR Data URI hợp lệ"
            );
        }

        return new ProviderCheckoutResult(null, null, response.data().qrDataURL());
    }

    private String quickLink(long amount, String addInfo) {
        return properties.getQuickLinkBaseUrl()
                + "/" + properties.getAcqId()
                + "-" + properties.getAccountNo()
                + "-" + properties.getTemplate()
                + ".png?amount=" + amount
                + "&addInfo=" + encode(addInfo)
                + "&accountName=" + encode(properties.getAccountName());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private VietQrGenerateResponse send(VietQrGenerateRequest request) {
        try {
            VietQrGenerateResponse response = restClient.post()
                    .uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-client-id", properties.getClientId())
                    .header("x-api-key", properties.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(VietQrGenerateResponse.class);
            if (response == null) {
                throw new PaymentProviderException(
                        "VIETQR_EMPTY_RESPONSE",
                        "VietQR trả về response rỗng"
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new PaymentProviderException(
                    "VIETQR_HTTP_ERROR",
                    "VietQR trả về HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new PaymentProviderException(
                    "VIETQR_CONNECTION_ERROR",
                    "Không thể kết nối tới VietQR",
                    exception
            );
        }
    }

    private long providerAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new PaymentProviderException("VIETQR_INVALID_AMOUNT", "Số tiền VietQR không hợp lệ");
        }
        try {
            long value = BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
            if (value > MAX_AMOUNT) {
                throw new PaymentProviderException(
                        "VIETQR_INVALID_AMOUNT",
                        "Số tiền VietQR không được vượt quá 13 chữ số"
                );
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new PaymentProviderException(
                    "VIETQR_INVALID_AMOUNT",
                    "Số tiền VietQR phải là số nguyên VND",
                    exception
            );
        }
    }

    private String transferContent(ProviderCheckoutCommand command) {
        if (command.getAttemptId() == null) {
            throw new PaymentProviderException("VIETQR_REFERENCE_REQUIRED", "Thiếu mã payment attempt");
        }
        String token = command.getAttemptId().toString().replace("-", "").toUpperCase();
        return "GS" + token.substring(0, 20);
    }

    private String safeCode(String code) {
        return code == null || code.isBlank() ? "UNKNOWN" : code.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String safeDescription(String description) {
        return description == null || description.isBlank() ? "Không có mô tả" : description;
    }

    private static SimpleClientHttpRequestFactory requestFactory(VietQrProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record VietQrGenerateRequest(
            String accountNo,
            String accountName,
            String acqId,
            long amount,
            String addInfo,
            String format,
            String template
    ) {
    }

    private record VietQrGenerateResponse(String code, String desc, VietQrGenerateData data) {
    }

    private record VietQrGenerateData(
            String acpId,
            String accountName,
            String qrCode,
            String qrDataURL
    ) {
    }
}
