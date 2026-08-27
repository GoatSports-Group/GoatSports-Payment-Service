package iuh.fit.paymentservice.infrastructure.external.config;

import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.provider.momo")
public class MomoProperties {
    private boolean enabled;
    private String apiUrl;
    private String redirectUrl;
    private String ipnUrl;
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String storeName = "GoatSports";
    private String storeId = "GoatSports";
    private String requestType = "captureWallet";
    private int timeoutSeconds = 30;

    @PostConstruct
    void validateWhenEnabled() {
        if (!enabled) {
            return;
        }
        if (isBlank(apiUrl) || !hasScheme(apiUrl, "https")) {
            throw configurationError("MOMO_API_URL phải là HTTPS URL do MoMo cung cấp");
        }
        if (isBlank(redirectUrl) || !hasHttpScheme(redirectUrl)) {
            throw configurationError("MOMO_REDIRECT_URL phải là HTTP(S) URL hợp lệ");
        }
        if (isBlank(ipnUrl) || !hasHttpScheme(ipnUrl)) {
            throw configurationError("MOMO_IPN_URL phải là HTTP(S) URL hợp lệ");
        }
        if (isBlank(partnerCode) || partnerCode.length() > 50) {
            throw configurationError("MOMO_PARTNER_CODE chưa được cấu hình hoặc vượt quá 50 ký tự");
        }
        if (isBlank(accessKey)) {
            throw configurationError("MOMO_ACCESS_KEY chưa được cấu hình");
        }
        if (isBlank(secretKey)) {
            throw configurationError("MOMO_SECRET_KEY chưa được cấu hình");
        }
        if (isBlank(storeName)) {
            throw configurationError("MOMO_STORE_NAME chưa được cấu hình");
        }
        if (isBlank(storeId) || storeId.length() > 50) {
            throw configurationError("MOMO_STORE_ID chưa được cấu hình hoặc vượt quá 50 ký tự");
        }
        if (!"captureWallet".equals(requestType)) {
            throw configurationError("Payment Service chỉ hỗ trợ MOMO_REQUEST_TYPE=captureWallet");
        }
        if (timeoutSeconds < 30 || timeoutSeconds > 120) {
            throw configurationError("MOMO_TIMEOUT_SECONDS phải nằm trong khoảng 30-120 giây");
        }
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new PaymentProviderException(
                    "MOMO_DISABLED",
                    "Kênh thanh toán MoMo chưa được bật"
            );
        }
    }

    private boolean hasScheme(String value, String expectedScheme) {
        try {
            return expectedScheme.equalsIgnoreCase(URI.create(value).getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasHttpScheme(String value) {
        return hasScheme(value, "http") || hasScheme(value, "https");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PaymentProviderException configurationError(String message) {
        return new PaymentProviderException("MOMO_CONFIGURATION_ERROR", message);
    }
}
