package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.ProviderCheckoutResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;

@Component
public class MomoCheckoutAdapter implements PaymentCheckoutPort {
    private static final long MIN_AMOUNT = 1_000L;
    private static final long MAX_AMOUNT = 50_000_000L;
    private static final String EMPTY_EXTRA_DATA = "";

    private final MomoProperties properties;
    private final MomoSignatureService signatureService;
    private final RestClient restClient;

    @Autowired
    public MomoCheckoutAdapter(MomoProperties properties, MomoSignatureService signatureService) {
        this(
                properties,
                signatureService,
                RestClient.builder().requestFactory(requestFactory(properties)).build()
        );
    }

    MomoCheckoutAdapter(MomoProperties properties, RestClient restClient) {
        this(properties, new MomoSignatureService(), restClient);
    }

    MomoCheckoutAdapter(
            MomoProperties properties,
            MomoSignatureService signatureService,
            RestClient restClient
    ) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.restClient = restClient;
    }

    @Override
    public ProviderCheckoutResult createCheckout(ProviderCheckoutCommand command) {
        properties.requireEnabled();
        validateMethod(command.getMethod());

        long amount = providerAmount(command.getAmount());
        String orderId = providerReference(command);
        String requestId = orderId;
        String orderInfo = orderInfo(command.getOrderInfo());
        String lang = language(command.getLocale());
        String rawSignature = rawRequestSignature(amount, orderId, orderInfo, requestId);
        String signature = hmacSha256(properties.getSecretKey(), rawSignature);

        MomoCreatePaymentRequest request = new MomoCreatePaymentRequest(
                properties.getPartnerCode(),
                properties.getStoreName(),
                properties.getStoreId(),
                requestId,
                amount,
                orderId,
                orderInfo,
                properties.getRedirectUrl(),
                properties.getIpnUrl(),
                properties.getRequestType(),
                EMPTY_EXTRA_DATA,
                true,
                lang,
                signature
        );

        MomoCreatePaymentResponse response = send(request);
        validateResponse(response, amount, orderId, requestId);
        return new ProviderCheckoutResult(
                response.payUrl(),
                response.deeplink(),
                directQrContent(response)
        );
    }

    String rawRequestSignature(long amount, String orderId, String orderInfo, String requestId) {
        return "accessKey=" + properties.getAccessKey()
                + "&amount=" + amount
                + "&extraData=" + EMPTY_EXTRA_DATA
                + "&ipnUrl=" + properties.getIpnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + properties.getPartnerCode()
                + "&redirectUrl=" + properties.getRedirectUrl()
                + "&requestId=" + requestId
                + "&requestType=" + properties.getRequestType();
    }

    String hmacSha256(String secret, String data) {
        return signatureService.hmacSha256(secret, data);
    }

    private MomoCreatePaymentResponse send(MomoCreatePaymentRequest request) {
        try {
            MomoCreatePaymentResponse response = restClient.post()
                    .uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MomoCreatePaymentResponse.class);
            if (response == null) {
                throw new PaymentProviderException(
                        "MOMO_EMPTY_RESPONSE",
                        "MoMo trả về response rỗng"
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new PaymentProviderException(
                    "MOMO_HTTP_ERROR",
                    "MoMo trả về HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new PaymentProviderException(
                    "MOMO_CONNECTION_ERROR",
                    "Không thể kết nối tới MoMo",
                    exception
            );
        }
    }

    private void validateResponse(
            MomoCreatePaymentResponse response,
            long expectedAmount,
            String expectedOrderId,
            String expectedRequestId
    ) {
        if (response.resultCode() == null) {
            throw new PaymentProviderException("MOMO_INVALID_RESPONSE", "MoMo không trả về resultCode");
        }
        if (response.resultCode() != 0) {
            throw new PaymentProviderException(
                    "MOMO_RESULT_" + response.resultCode(),
                    "MoMo từ chối tạo giao dịch: " + safeMessage(response.message())
            );
        }
        if (!properties.getPartnerCode().equals(response.partnerCode())
                || !expectedOrderId.equals(response.orderId())
                || !expectedRequestId.equals(response.requestId())
                || response.amount() == null
                || response.amount() != expectedAmount) {
            throw new PaymentProviderException(
                    "MOMO_RESPONSE_MISMATCH",
                    "Thông tin response từ MoMo không khớp với request"
            );
        }
        if (!isHttpsUrl(response.payUrl())) {
            throw new PaymentProviderException(
                    "MOMO_INVALID_PAY_URL",
                    "MoMo không trả về payUrl HTTPS hợp lệ"
            );
        }
        verifyResponseSignatureIfPresent(response);
    }

    private String directQrContent(MomoCreatePaymentResponse response) {
        if (response.qrCodeUrl() != null && !response.qrCodeUrl().isBlank()) {
            return response.qrCodeUrl();
        }
        if (response.deeplink() != null && !response.deeplink().isBlank()) {
            return response.deeplink();
        }
        return null;
    }

    private void verifyResponseSignatureIfPresent(MomoCreatePaymentResponse response) {
        if (response.signature() == null || response.signature().isBlank()) {
            return;
        }
        String expected = hmacSha256(properties.getSecretKey(), rawResponseSignature(response));
        if (!signatureService.matches(expected, response.signature())) {
            throw new PaymentProviderException(
                    "MOMO_INVALID_RESPONSE_SIGNATURE",
                    "Chữ ký response từ MoMo không hợp lệ"
            );
        }
    }

    private String rawResponseSignature(MomoCreatePaymentResponse response) {
        return "accessKey=" + properties.getAccessKey()
                + "&amount=" + response.amount()
                + "&message=" + response.message()
                + "&orderId=" + response.orderId()
                + "&partnerCode=" + response.partnerCode()
                + "&payUrl=" + response.payUrl()
                + "&requestId=" + response.requestId()
                + "&responseTime=" + response.responseTime()
                + "&resultCode=" + response.resultCode();
    }

    private long providerAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new PaymentProviderException("MOMO_INVALID_AMOUNT", "Số tiền gửi MoMo không hợp lệ");
        }
        try {
            long value = BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
            if (value < MIN_AMOUNT || value > MAX_AMOUNT) {
                throw new PaymentProviderException(
                        "MOMO_INVALID_AMOUNT",
                        "Số tiền MoMo phải nằm trong khoảng 1.000-50.000.000 VND"
                );
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new PaymentProviderException(
                    "MOMO_INVALID_AMOUNT",
                    "Số tiền MoMo phải là số nguyên VND",
                    exception
            );
        }
    }

    private String providerReference(ProviderCheckoutCommand command) {
        if (command.getAttemptId() == null) {
            throw new PaymentProviderException("MOMO_REFERENCE_REQUIRED", "Thiếu mã payment attempt");
        }
        return command.getAttemptId().toString().replace("-", "");
    }

    private String orderInfo(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new PaymentProviderException(
                    "MOMO_INVALID_ORDER_INFO",
                    "Nội dung thanh toán MoMo phải có từ 1 đến 255 ký tự"
            );
        }
        return value;
    }

    private String language(String locale) {
        if (locale == null || locale.isBlank() || "vn".equalsIgnoreCase(locale)) {
            return "vi";
        }
        if ("en".equalsIgnoreCase(locale)) {
            return "en";
        }
        throw new PaymentProviderException("MOMO_INVALID_LOCALE", "MoMo chỉ hỗ trợ locale vi hoặc en");
    }

    private void validateMethod(PaymentMethod method) {
        if (method != PaymentMethod.E_WALLET) {
            throw new PaymentProviderException(
                    "UNSUPPORTED_PAYMENT_METHOD",
                    "Checkout MoMo chỉ hỗ trợ phương thức E_WALLET"
            );
        }
    }

    private boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "Không có mô tả" : message;
    }

    private static SimpleClientHttpRequestFactory requestFactory(MomoProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record MomoCreatePaymentRequest(
            String partnerCode,
            String storeName,
            String storeId,
            String requestId,
            long amount,
            String orderId,
            String orderInfo,
            String redirectUrl,
            String ipnUrl,
            String requestType,
            String extraData,
            boolean autoCapture,
            String lang,
            String signature
    ) {
    }

    private record MomoCreatePaymentResponse(
            String partnerCode,
            String requestId,
            String orderId,
            Long amount,
            Long responseTime,
            String message,
            Integer resultCode,
            String payUrl,
            String deeplink,
            String qrCodeUrl,
            String signature
    ) {
    }
}
